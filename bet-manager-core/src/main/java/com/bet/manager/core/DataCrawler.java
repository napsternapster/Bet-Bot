package com.bet.manager.core;

import com.bet.manager.core.exceptions.IllegalTeamMappingException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class DataCrawler {

	private static final Logger log = LoggerFactory.getLogger(DataCrawler.class);

	private static final int ROUNDS = 34;

	private static final String BUNDESLIGA_DOMAIN = "http://www.bundesliga.com/";
	private static final String STATS_URL = "data/feed/51/%s/team_stats_round/team_stats_round_%s.xml?cb=544329";
	private static final String ROUND_MATCHES_URL = "data/feed/51/%s/post_standing/post_standing_%s.xml?cb=517837";
	private static final String TEAM_STATS_URL = "data/feed/51/%s/team_stats_round/team_stats_round_%s.xml?cb=544329";

	private static final String TRACK_DIST_ATTR = "imp:tracking-distance";
	private static final String TRACK_SPRINTS_ATTR = "imp:tracking-sprints";
	private static final String TRACK_PASSES_ATTR = "imp:passes-total";
	private static final String TRACK_SHOTS_ATTR = "shots-total";
	private static final String TRACK_FOULS_ATTR = "fouls-committed";

	private static final String RESULTDB_DOMAIN = "http://www.resultdb.com/";
	private static final String RESULTDB_MATCHES_FOR_TEAM_URL = "germany/%s/%s/";

	private static final String TABLE_SELECTOR = "table.results";
	private static final String TEAM_ATTR = "team";
	private static final String TEAM_KEY_ATTR = "team-key";
	private static final String CODE_NAME_ATTR = "code-name";
	private static final String CODE_KEY_ATTR = "code-key";
	private static final String CODE_TYPE_ATTR = "code-type";
	private static final String GROUP_STATS_ATTR = "group-stats";
	private static final String TEAM_ID_SPLITERATOR = "soccer.t_";
	private static final String SPORTS_CONTENT_ATTR = "sports-content-code";
	private static final String AWAY_TEAM_LITERAL = "Away";
	private static final String DRAW_GAME_LITERAL = "D";
	private static final String WIN_GAME_LITERAL = "W";
	private static final String LOSE_GAME_LITERAL = "L";
	private static final String RESUL_SPLITERATOR = "-";

	private static final WebCrawler crawler = new WebCrawler();

	private DataCrawler() {
	}

	/**
	 * This method creating data for every round matches during given year in the bundesliga
	 * german football league. This method doing multiple inner crawlings which requiring internet connection.
	 *
	 * @param year for which want to get the data
	 * @return {@link List<String>} containing list of data for every match
	 */
	public static List<String> getDataForAllMacthes(int year) {

		log.info("Start collecting data for year {}", year);

		List<String> allData = new ArrayList<>();

		// We skip the first round because for the current match we only looking for the previous one data
		for (int round = 2; round <= ROUNDS; round++) {

			try {
				List<String> currentRoundEntries = createDataForRound(year, round);
				allData.addAll(currentRoundEntries);
				log.info("Successfully created data for {} matches for round {}.", currentRoundEntries.size(), round);
			} catch (Exception e) {
				log.error("Failed to create data for {} round {}." + System.lineSeparator() + "{}",
						year,
						round,
						e.getMessage());
			}
		}

		log.info("Successfully created {} data entries for year {}", allData.size(), year);
		return allData;
	}

	/**
	 * Creating data for given round and year. This method containing multiple inner
	 * crawlings which requiring internet connection
	 *
	 * @param year
	 * @param round
	 * @return {@link List<String>} matches for current {@param year} and {@param round}
	 * @throws MalformedURLException if the string xml cannot be parsed as document
	 * @throws InterruptedException  if the current thread cannot sleep
	 */
	private static List<String> createDataForRound(int year, int round)
			throws MalformedURLException, InterruptedException {

		String prevRoundMatchesContent = getMatchesContent(year, round - 1);
		String currentRoundMatchesContent = getMatchesContent(year, round);

		Document previousRoundMatchesXML = DocumentUtils.parse(prevRoundMatchesContent);
		Document currentRoundMatchesXML = DocumentUtils.parse(currentRoundMatchesContent);

		Map<String, Integer> currentRoundRanking = getRoundRanking(currentRoundMatchesXML);
		Map<String, Double> prevRoundAverageStats = parseAverageRoundStats(year, round - 1);

		NodeList teams = currentRoundMatchesXML.getElementsByTagName(TEAM_ATTR);

		List<String> dataRows = new ArrayList<>();
		Set<String> teamBlackList = new HashSet<>();

		for (int i = 0; i < teams.getLength(); i++) {

			StringBuilder currentRowData = new StringBuilder();
			Node currentTeam = teams.item(i);

			String homeTeam = getTeamName(currentTeam);
			String[] opponentTeamAndVenue = parseCurrentTeamOpponentAndVenue(homeTeam, year, round);
			String opponentTeam = opponentTeamAndVenue[0];

			String currentTeamVenue = opponentTeamAndVenue[1];
			String opponentVenue = currentTeamVenue == "1" ? "-1" : "1";

			if (teamBlackList.contains(homeTeam) || teamBlackList.contains(opponentTeam)) {
				continue;
			}

			teamBlackList.add(homeTeam);
			teamBlackList.add(opponentTeam);

			currentRowData.append(round + " ");

			currentRowData
					.append(getDataForTeam(homeTeam, currentRoundMatchesXML, year, round, currentTeamVenue,
							prevRoundAverageStats, currentRoundRanking) + " ");
			currentRowData
					.append(getDataForTeam(opponentTeam, currentRoundMatchesXML, year, round, opponentVenue,
							prevRoundAverageStats, currentRoundRanking) + " ");

			currentRowData.append(getLastTwoMatchesBetween(homeTeam, opponentTeam, previousRoundMatchesXML));

			dataRows.add(currentRowData.toString());
		}

		return dataRows;
	}

	private static String getMatchesContent(int year, int round) throws MalformedURLException {
		URL prevRoundMatchesURL = new URL(String.format(BUNDESLIGA_DOMAIN + ROUND_MATCHES_URL, year, round));
		return crawler.crawl(prevRoundMatchesURL);
	}

	/**
	 * Create ranking map for the teams
	 *
	 * @param doc parsed as xml
	 * @return {@link Map<String, Integer>} containing pairs {team} => {rank}
	 */
	public static Map<String, Integer> getRoundRanking(Document doc) {

		Map<String, Integer> ranking = new HashMap<>();
		NodeList teamNodes = doc.getElementsByTagName(SPORTS_CONTENT_ATTR);

		for (int i = 0; i < teamNodes.getLength(); i++) {

			Node currentTeam = teamNodes.item(i);
			NamedNodeMap attributes = currentTeam.getAttributes();

			if (attributes.getNamedItem(CODE_TYPE_ATTR).getNodeValue().equals(TEAM_ATTR)) {

				int teamId =
						Integer.parseInt(
								attributes.getNamedItem(CODE_KEY_ATTR).getNodeValue().split(TEAM_ID_SPLITERATOR)[1]);
				String teamName = attributes.getNamedItem(CODE_NAME_ATTR).getNodeValue();

				checkIfNameIsCorrespondingToTeam(teamId, teamName);

				ranking.put(teamName, ranking.size() + 1);
			}
		}

		return ranking;
	}

	/**
	 * Method which check if the mapping from {@link BundesLiga} is correct with the
	 * bundesliga crawled id. This is pretty necessary because otherwise the data should be non-corresponding
	 *
	 * @param id   team id
	 * @param team team name
	 * @return true if the mapping is correct, false otherwise
	 */
	private static boolean checkIfNameIsCorrespondingToTeam(int id, String team) {
		if (BundesLiga.teams.get(id).equals(team)) {
			return true;
		}

		log.error("Team with id {} is mapped to {}, but in the xml team node id {} is mapped to {}",
				id, team,
				id, BundesLiga.teams.get(id));

		throw new IllegalTeamMappingException("Invalid mapping found in hash map and the xml team node");
	}

	private static Map<String, Double> parseAverageRoundStats(int year, int round)
			throws MalformedURLException {

		URL prevRoundStatsURL = new URL(String.format(BUNDESLIGA_DOMAIN + STATS_URL, year, round - 1));
		String prevRoundStatsXML = crawler.crawl(prevRoundStatsURL);
		return getAverageRoundStats(prevRoundStatsXML);
	}

	/**
	 * Method which creates {@link Map<String, Double>} with pairs {key} => {value}
	 * for different statistics (track distance, sprints, goals, fouls, passes)
	 *
	 * @param prevRoundStatsXML xml containing the previous round statistics
	 * @return @link Map<String, Double>} with average statistics for given round and year
	 * @throws MalformedURLException if the url cannot be crawled
	 */
	public static Map<String, Double> getAverageRoundStats(String prevRoundStatsXML) {

		Document doc = DocumentUtils.parse(prevRoundStatsXML);
		NodeList teamNodes = doc.getElementsByTagName(GROUP_STATS_ATTR);

		NamedNodeMap statsAttributes = teamNodes.item(0).getAttributes();
		Map<String, Double> averageStats = new HashMap<>();

		addAttribute(TRACK_DIST_ATTR, statsAttributes, averageStats);
		addAttribute(TRACK_SPRINTS_ATTR, statsAttributes, averageStats);
		addAttribute(TRACK_PASSES_ATTR, statsAttributes, averageStats);
		addAttribute(TRACK_SHOTS_ATTR, statsAttributes, averageStats);
		addAttribute(TRACK_FOULS_ATTR, statsAttributes, averageStats);

		return averageStats;
	}

	private static void addAttribute(String attrName, NamedNodeMap statsAttributes, Map<String, Double> averageStats) {
		Node attributeNode = statsAttributes.getNamedItem(attrName);
		averageStats.put(attributeNode.getNodeName(), Double.parseDouble(attributeNode.getNodeValue()));
	}

	private static String getTeamName(Node currentTeam) {
		Node teamMetaDataNode = currentTeam.getFirstChild().getNextSibling();
		int teamId = Integer.parseInt(teamMetaDataNode.getAttributes().getNamedItem(TEAM_KEY_ATTR).getNodeValue());
		return BundesLiga.teams.get(teamId);
	}

	private static String[] parseCurrentTeamOpponentAndVenue(String homeTeam, int year, int round)
			throws MalformedURLException {

		String resultDBTeamName = ResultDB.bundesLigaMappingToResultDB.get(homeTeam);
		URL resultDBUrl =
				new URL(String.format(RESULTDB_DOMAIN + RESULTDB_MATCHES_FOR_TEAM_URL, resultDBTeamName, year));

		String content = crawler.crawl(resultDBUrl);

		return getCurrentTeamOpponentAndVenue(content, round);
	}

	/**
	 * Method parses the given html as string and return the target round match opponent and venue
	 * Venue is Away or Home. The Venue is -1 if the current team is Away and 1 if the current team is Home
	 *
	 * @param allMatchesHTML contains the arbitrary team all matches for arbitrary year
	 * @param round          to know which opponent should be returned
	 * @return the opponent and venue stored in array (0 => opponent, 1 => venue)
	 */
	public static String[] getCurrentTeamOpponentAndVenue(String allMatchesHTML, int round) {

		org.jsoup.nodes.Document doc = Jsoup.parse(allMatchesHTML);
		Element e = doc.body().select(TABLE_SELECTOR).get(0);

		int matchesCount = e.children().get(0).children().size();
		Element currentMatch = e.children().get(0).children().get(matchesCount - round);

		String opponent = currentMatch.children().get(1).text();
		String venue = currentMatch.children().get(2).text().equals(AWAY_TEAM_LITERAL) ? "-1" : "1";

		return new String[] { opponent, venue };
	}

	/**
	 * Method which will be used to collect data for given team for current year and specific found.
	 * This method containing multiple inner crawling, so its requiring internet connection.
	 *
	 * @param teamName              which we want to get data
	 * @param currentRoundStats     previous round matches information as {@link Document}
	 * @param year                  for which we want to get information
	 * @param round                 for which we want to get information
	 * @param venue                 for current team and its Home(1) or Away(-1), depends for the match
	 * @param prevRoundAverageStats average stats for the previous round. This is useful if the crawled data is
	 *                              not fully included and some of the fields are left empty, if so the average
	 *                              data will be used instead.
	 * @param currentRoundRanking   Position in the ranking for the current and round
	 * @return all of the data information as {@link String}
	 */
	public static String getDataForTeam(String teamName, Document currentRoundStats, int year, int round,
			String venue, Map<String, Double> prevRoundAverageStats, Map<String, Integer> currentRoundRanking)
			throws MalformedURLException {

		StringBuilder currentTeamData = new StringBuilder();

		currentTeamData.append(getTeamRankingPlace(teamName, currentRoundRanking) + " ");
		currentTeamData.append(getCurrentRankingStats(teamName, currentRoundStats) + " ");
		currentTeamData.append(venue + " ");
		currentTeamData.append(parsePrevRoundTeamPerformance(teamName, year, round, prevRoundAverageStats) + " ");
		currentTeamData.append(parseResultsForPastFiveGames(teamName, year, round));

		return currentTeamData.toString();
	}

	private static Integer getTeamRankingPlace(String teamName, Map<String, Integer> currentRoundRanking) {
		return currentRoundRanking.get(teamName);
	}

	/**
	 * Get the current round goals difference and points for given team
	 *
	 * @param teamName
	 * @param currentRoundMatches
	 * @return
	 */
	public static String getCurrentRankingStats(String teamName, Document currentRoundMatches) {
		return null;
	}

	private static String parsePrevRoundTeamPerformance(String teamName, int year, int round,
			Map<String, Double> prevRoundAverageStats) throws MalformedURLException {

		URL prevRoundStatsURL = new URL(String.format(BUNDESLIGA_DOMAIN + TEAM_STATS_URL, year, round - 1));
		String prevRoundTeamStatsXML = crawler.crawl(prevRoundStatsURL);
		return getPrevRoundTeamPerformance(prevRoundTeamStatsXML, teamName, prevRoundAverageStats);
	}

	/**
	 * Get previous performance for team and collects the data about total distance, sprints,
	 * passes, shots, fouls and if any of them is leaved empty the value
	 * from the average round stats will come in place
	 *
	 * @param prevRoundTeamStatsXML represent the prev round statistics as xml
	 * @param team                  which wanna get performance
	 * @param prevRoundAverageStats {@link Map<String, Double>} containing the average values
	 * @return
	 */
	public static String getPrevRoundTeamPerformance(String prevRoundTeamStatsXML, String team,
			Map<String, Double> prevRoundAverageStats) {

		StringBuilder prevRoundStats = new StringBuilder();

		Document doc = DocumentUtils.parse(prevRoundTeamStatsXML);
		NodeList teamNodes = doc.getElementsByTagName(TEAM_ATTR);

		for (int i = 0; i < teamNodes.getLength(); i++) {

			Node currentTeam = teamNodes.item(i);
			String currentTeamName = getNameFromTeamNode(currentTeam);

			if (currentTeamName.equals(team)) {
				prevRoundStats.append(getPRevRoundStats(currentTeam, prevRoundAverageStats));
				break;
			}
		}

		return prevRoundStats.toString();
	}

	private static String getPRevRoundStats(Node currentTeam, Map<String, Double> prevRoundAverageStats) {

		StringBuilder output = new StringBuilder();

		NamedNodeMap defensiveAttributes =
				currentTeam.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getFirstChild()
						.getNextSibling().getAttributes();

		output.append(getAttribute(TRACK_DIST_ATTR, prevRoundAverageStats, defensiveAttributes) + " ");
		output.append(getAttribute(TRACK_SPRINTS_ATTR, prevRoundAverageStats, defensiveAttributes) + " ");
		output.append(getAttribute(TRACK_PASSES_ATTR, prevRoundAverageStats, defensiveAttributes) + " ");

		NamedNodeMap offensiveAttributes =
				currentTeam.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getFirstChild()
						.getNextSibling().getFirstChild().getNextSibling().getAttributes();

		output.append(getAttribute(TRACK_SHOTS_ATTR, prevRoundAverageStats, offensiveAttributes) + " ");

		NamedNodeMap foulsAttribute =
				currentTeam.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getFirstChild()
						.getNextSibling().getFirstChild().getNextSibling().getNextSibling().getNextSibling()
						.getAttributes();

		output.append(getAttribute(TRACK_FOULS_ATTR, prevRoundAverageStats, foulsAttribute));

		return output.toString().trim();
	}

	private static String getAttribute(String attrName, Map<String, Double> prevRoundAverageStats,
			NamedNodeMap defensiveAttributes) {

		String attrValue;

		if (defensiveAttributes.getNamedItem(attrName).getNodeValue() == "") {
			attrValue = prevRoundAverageStats.get(attrName).toString();
		} else {
			attrValue = defensiveAttributes.getNamedItem(attrName).getNodeValue();
		}

		return attrValue;
	}

	private static String getNameFromTeamNode(Node team) {
		return team.getFirstChild().getNextSibling().getFirstChild().getNextSibling().getAttributes()
				.getNamedItem("full").getNodeValue();
	}

	private static String parseResultsForPastFiveGames(String teamName, int year, int round)
			throws MalformedURLException {

		String resultDBTeamName = ResultDB.bundesLigaMappingToResultDB.get(teamName);
		URL resultDBUrl =
				new URL(String.format(RESULTDB_DOMAIN + RESULTDB_MATCHES_FOR_TEAM_URL, resultDBTeamName, year));

		String content = crawler.crawl(resultDBUrl);

		return getResultsForPastFiveGames(content, round);
	}

	/**
	 * Collects data for last five matches for given team. If the matches before the
	 * given round is less than five will take the available ones.
	 *
	 * @param allMatchesHTML containing all the matches for given team
	 * @param round          to get the last matches before the round
	 * @return data format ({HugeWin} {HugeLoss} {Win} {Loss} {Tie}). WHen the huge outcome
	 * is made if the absolute value between the goals is greater than 1
	 */
	public static String getResultsForPastFiveGames(String allMatchesHTML, int round) {

		org.jsoup.nodes.Document doc = Jsoup.parse(allMatchesHTML);
		Element e = doc.body().select(TABLE_SELECTOR).get(0);

		int matchesCount = e.children().get(0).children().size();

		int[] matchesNormalization = new int[5];
		int matchesToLook = Math.min(round - 1, 5);

		for (int i = 0; i < matchesToLook; i++) {
			Element currentMatch = e.children().get(0).children().get(matchesCount - round + i + 1);

			String result = currentMatch.children().get(3).text();
			String score = currentMatch.children().get(4).text();
			addMatchToNormalizationArray(result, score, matchesNormalization);
		}

		StringBuilder output = new StringBuilder();
		for (int i = 0; i < matchesNormalization.length; i++) {
			output.append(matchesNormalization[i] + " ");
		}

		return output.toString().trim();
	}

	/**
	 * Function which update the matchesNormalization param with the given score depends
	 * of the result. For huge outcome is accepted if the score difference is bigger than 1
	 * and the result can be one of the following {W,L,D}
	 *
	 * @param result               W,L,D
	 * @param score                e.g (4-2)
	 * @param matchesNormalization contains array with 4 options {HugeWin, HugeLoss, Win, Loss, Tie}
	 */
	public static void addMatchToNormalizationArray(String result, String score, int[] matchesNormalization) {

		String[] rawNumbers = score.split(RESUL_SPLITERATOR);
		int differenceInScore =
				Math.abs(Integer.parseInt(rawNumbers[0].trim()) - Integer.parseInt(rawNumbers[1].trim()));

		switch (result) {
		case DRAW_GAME_LITERAL:
			matchesNormalization[4]++;
			break;
		case WIN_GAME_LITERAL:
			if (differenceInScore > 1) {
				matchesNormalization[0]++;
			} else {
				matchesNormalization[2]++;
			}
			break;
		case LOSE_GAME_LITERAL:
			if (differenceInScore > 1) {
				matchesNormalization[1]++;
			} else {
				matchesNormalization[3]++;
			}
			break;
		default:
			throw new IllegalArgumentException("Invalid result state " + result);
		}
	}

	private static String getLastTwoMatchesBetween(String homeTeam, String awayTeam, Document xmlDocument) {
		return "";
	}
}