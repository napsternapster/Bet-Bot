package com.bet.manager.core.data.sources;

import com.bet.manager.commons.util.DocumentUtils;
import com.bet.manager.commons.util.URLUtils;
import com.bet.manager.core.TeamsMapping;
import com.bet.manager.core.WebCrawler;
import com.bet.manager.core.exceptions.IllegalTeamMappingException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Bundesliga {

	private static final Logger log = LoggerFactory.getLogger(Bundesliga.class);

	private static final String BUNDESLIGA_DOMAIN = "http://www.bundesliga.com/";
	private static final String ROUND_MATCHES_URL = "data/feed/51/%s/post_standing/post_standing_%s.xml?cb=517837";
	private static final String TEAM_STATS_URL = "data/feed/51/%s/team_stats_round/team_stats_round_%s.xml?cb=544329";

	private static final String TRACK_DIST_ATTR = "imp:tracking-distance";
	private static final String TRACK_SPRINTS_ATTR = "imp:tracking-sprints";
	private static final String TRACK_PASSES_ATTR = "imp:passes-total";
	private static final String TRACK_SHOTS_ATTR = "shots-total";
	private static final String TRACK_FOULS_ATTR = "fouls-committed";

	private static final String TEAM_ATTR = "team";
	private static final String TEAM_KEY_ATTR = "team-key";
	private static final String CODE_NAME_ATTR = "code-name";
	private static final String CODE_KEY_ATTR = "code-key";
	private static final String CODE_TYPE_ATTR = "code-type";
	private static final String TEAM_ID_SPLITERATOR = "soccer.t_";
	private static final String SPORTS_CONTENT_ATTR = "sports-content-code";
	private static final String GROUP_STATS_ATTR = "group-stats";

	private Bundesliga() {
	}

	/**
	 * Method which returns the position in the ranking table for team in given
	 * round and year. This method uses external memorization maps for already crawled
	 * pages and already parsed documents.
	 *
	 * @param team            team name
	 * @param year            match year
	 * @param round           match round
	 * @param crawledPages    memorization map for already crawled pages
	 * @param parsedDocuments memorization map for already parsed documents
	 * @return current round place
	 */
	public static Integer getTeamRankingPlace(String team, int year, int round,
			Map<URL, String> crawledPages, Map<String, Document> parsedDocuments)
			throws MalformedURLException, InterruptedException {

		log.debug("Getting information about '{}' rank in round {} year {}", team, round, year);
		String currentRoundMatchesContent = Bundesliga.getMatches(year, round, crawledPages);

		Document currentRoundMatchesXML = DocumentUtils.parse(currentRoundMatchesContent, parsedDocuments);

		log.debug("Creating ranking table for round {} year {}", round, year);
		Map<String, Integer> currentRoundRanking = Bundesliga.createRankingTable(currentRoundMatchesXML);

		return currentRoundRanking.get(team);
	}

	/**
	 * Getting all matches for given round and year.
	 *
	 * @param year         year of the matches
	 * @param round        round of the matches
	 * @param crawledPages memorization map for already crawled pages
	 * @return content page with all matches for the round in html format
	 */
	public static String getMatches(int year, int round, Map<URL, String> crawledPages)
			throws MalformedURLException, InterruptedException {

		URL prevRoundMatchesURL =
				URLUtils.createSafeURL(String.format(BUNDESLIGA_DOMAIN + ROUND_MATCHES_URL, year, round));

		return WebCrawler.crawl(prevRoundMatchesURL, crawledPages);
	}

	/**
	 * Create ranking table from the document
	 *
	 * @param doc parsed as xml
	 * @return Map containing pairs {team} => {rank}
	 */
	public static Map<String, Integer> createRankingTable(Document doc) {

		Map<String, Integer> ranking = new HashMap<>();
		NodeList teamNodes = doc.getElementsByTagName(SPORTS_CONTENT_ATTR);
		log.debug("Found {} teams in the rank table", teamNodes.getLength());

		for (int i = 0; i < teamNodes.getLength(); i++) {

			Node currentTeam = teamNodes.item(i);
			NamedNodeMap attributes = currentTeam.getAttributes();

			if (attributes.getNamedItem(CODE_TYPE_ATTR).getNodeValue().equals(TEAM_ATTR)) {

				int teamId =
						Integer.parseInt(
								attributes.getNamedItem(CODE_KEY_ATTR).getNodeValue().split(TEAM_ID_SPLITERATOR)[1]);
				String teamName = attributes.getNamedItem(CODE_NAME_ATTR).getNodeValue();

				log.debug("Team {}. -> '{}'", ranking.size() + 1, teamName);
				checkForValidMappingBundesligaIdToTeam(teamId, teamName);

				ranking.put(teamName, ranking.size() + 1);
			}
		}

		return ranking;
	}

	private static boolean checkForValidMappingBundesligaIdToTeam(int id, String team) {
		log.debug("Searching for valid mapping from BundesligaID => BundesligaName");
		if (TeamsMapping.bundesligaIdToName.get(id).equals(team)) {
			return true;
		}

		throw new IllegalTeamMappingException("Team with id " + id + " is mapped to " + team +
				", but in the xml team node id " + id + " is mapped to " + TeamsMapping.bundesligaIdToName.get(id));
	}

	/**
	 * Get the current points and round goals difference for team
	 *
	 * @param team            Team for which you want to get the stats
	 * @param year            year for the match
	 * @param round           round fot the match
	 * @param crawledPages    memorization map for already crawled pages
	 * @param parsedDocuments memorization map for already parsed documents
	 * @return the ranking stats for team for given year and round
	 */
	public static String getCurrentRankingStats(String team, int year, int round, Map<URL, String> crawledPages,
			Map<String, Document> parsedDocuments)
			throws MalformedURLException, InterruptedException {

		String currentRoundMatchesContent = Bundesliga.getMatches(year, round, crawledPages);
		Document currentRoundMatches = DocumentUtils.parse(currentRoundMatchesContent, parsedDocuments);

		log.debug("Getting information for team '{}' in round {} year {} statistics", team, round, year);
		return parseCurrentRankingStats(team, currentRoundMatches);
	}

	/**
	 * @param team                team name
	 * @param currentRoundMatches Document containing statistics for specific round
	 * @return statistics for round
	 */
	public static String parseCurrentRankingStats(String team, Document currentRoundMatches) {

		StringBuilder prevRoundStats = new StringBuilder();

		NodeList teamNodes = currentRoundMatches.getElementsByTagName(TEAM_ATTR);

		for (int i = 0; i < teamNodes.getLength(); i++) {

			Node currentTeam = teamNodes.item(i);
			String currentTeamName = getNameFromTeamNode(currentTeam);

			if (currentTeamName.equals(team)) {
				prevRoundStats.append(getGoalDifferenceAndPoints(currentTeam));
				break;
			}
		}

		return prevRoundStats.toString();
	}

	private static String getGoalDifferenceAndPoints(Node currentTeam) {

		NamedNodeMap attributes =
				currentTeam.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getFirstChild()
						.getNextSibling().getAttributes();

		Integer goalDifference =
				Integer.parseInt(attributes.getNamedItem("points-scored-for").getNodeValue()) -
						Integer.parseInt(attributes.getNamedItem("points-scored-against").getNodeValue());
		log.debug("Goal difference : {}", goalDifference);

		String points = attributes.getNamedItem("standing-points").getNodeValue();
		log.debug("Points : {}", points);

		return points + " " + goalDifference;
	}

	/**
	 * Method which is getting information about previous round by given round team performance
	 * (collects the data about total distance, sprints, passes, shots, fouls and if any of them
	 * is leaved empty the value from the average round stats will come in place)
	 * This method using external memorization maps for already crawled pages and already parsed docs.
	 *
	 * @param team            which wanna get performance
	 * @param year            year for match
	 * @param round           round for match ( the method will call with round - 1, to get the previous )
	 * @param crawledPages    memorization map for already crawled pages
	 * @param parsedDocuments memorization map for already parsed documents
	 * @return statistics for team
	 */
	public static String getPrevRoundTeamPerformance(String team, int year, int round, Map<URL, String> crawledPages,
			Map<String, Document> parsedDocuments)
			throws MalformedURLException, InterruptedException {

		log.debug("Getting information for team '{}' in round {} year {}", team, round, year);
		Map<String, Integer> prevRoundAverageStats =
				Bundesliga.getAverageRoundStats(year, round - 1, crawledPages, parsedDocuments);

		URL prevRoundStatsURL =
				URLUtils.createSafeURL(String.format(BUNDESLIGA_DOMAIN + TEAM_STATS_URL, year, round - 1));

		String prevRoundTeamStatsXML = WebCrawler.crawl(prevRoundStatsURL, crawledPages);

		log.debug("Parsing the information collect in the previous round", team, round, year);
		return parsePrevRoundTeamPerformance(prevRoundTeamStatsXML, team, prevRoundAverageStats, parsedDocuments);
	}

	/**
	 * Method which is getting information about previous round by given round team performance
	 * (collects the data about total distance, sprints, passes, shots, fouls and if any of them
	 * is leaved empty the value from the average round stats will come in place)
	 *
	 * @param prevRoundTeamStatsXML represent the prev round statistics as xml
	 * @param team                  which wanna get performance
	 * @param prevRoundAverageStats map containing the average statistics for all teams
	 * @param parsedDocuments       memorization map for already parsed documents
	 * @return statistics for team
	 */
	public static String parsePrevRoundTeamPerformance(String prevRoundTeamStatsXML, String team,
			Map<String, Integer> prevRoundAverageStats, Map<String, Document> parsedDocuments) {

		StringBuilder prevRoundStats = new StringBuilder();

		Document doc = DocumentUtils.parse(prevRoundTeamStatsXML, parsedDocuments);
		NodeList teamNodes = doc.getElementsByTagName(TEAM_ATTR);

		for (int i = 0; i < teamNodes.getLength(); i++) {

			Node currentTeam = teamNodes.item(i);
			String currentTeamName = getNameFromTeamNode(currentTeam);

			if (currentTeamName.equals(team)) {
				prevRoundStats.append(getPrevRoundStats(currentTeam, prevRoundAverageStats));
				break;
			}
		}

		return prevRoundStats.toString();
	}

	private static String getNameFromTeamNode(Node team) {
		return team.getFirstChild().getNextSibling().getFirstChild().getNextSibling().getAttributes()
				.getNamedItem("full").getNodeValue();
	}

	private static String getPrevRoundStats(Node currentTeam, Map<String, Integer> prevRoundAverageStats) {

		StringBuilder output = new StringBuilder();

		NamedNodeMap defensiveAttributes =
				currentTeam.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getFirstChild()
						.getNextSibling().getAttributes();

		output.append(getAttribute(TRACK_DIST_ATTR, prevRoundAverageStats, defensiveAttributes)).append(" ");
		output.append(getAttribute(TRACK_SPRINTS_ATTR, prevRoundAverageStats, defensiveAttributes)).append(" ");
		output.append(getAttribute(TRACK_PASSES_ATTR, prevRoundAverageStats, defensiveAttributes)).append(" ");

		NamedNodeMap offensiveAttributes =
				currentTeam.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getFirstChild()
						.getNextSibling().getFirstChild().getNextSibling().getAttributes();

		output.append(getAttribute(TRACK_SHOTS_ATTR, prevRoundAverageStats, offensiveAttributes)).append(" ");

		NamedNodeMap foulsAttribute =
				currentTeam.getFirstChild().getNextSibling().getNextSibling().getNextSibling().getFirstChild()
						.getNextSibling().getFirstChild().getNextSibling().getNextSibling().getNextSibling()
						.getAttributes();

		output.append(getAttribute(TRACK_FOULS_ATTR, prevRoundAverageStats, foulsAttribute));

		return output.toString().trim();
	}

	private static String getAttribute(String attrName, Map<String, Integer> prevRoundAverageStats,
			NamedNodeMap defensiveAttributes) {

		String attrValue;

		if (StringUtils.isBlank(defensiveAttributes.getNamedItem(attrName).getNodeValue())) {
			attrValue = prevRoundAverageStats.get(attrName).toString();
		} else {
			attrValue = defensiveAttributes.getNamedItem(attrName).getNodeValue();
		}

		//Get the integer part of the number
		if (attrValue.contains(".")) {
			attrValue = attrValue.substring(0, attrValue.lastIndexOf("."));
		}

		log.debug("{} : {}", attrName, attrValue);
		return attrValue;
	}

	/**
	 * Method which creates Map with pairs {key} => {value}
	 * for different statistics (track distance, sprints, goals, fouls, passes)
	 *
	 * @param year         year for the match
	 * @param round        round for the match
	 * @param crawledPages memorization map for already crawled pages
	 * @return Map with average statistics for given round and year
	 */
	public static Map<String, Integer> getAverageRoundStats(int year, int round, Map<URL, String> crawledPages,
			Map<String, Document> parsedDocuments)
			throws MalformedURLException, InterruptedException {

		URL prevRoundStatsURL = URLUtils.createSafeURL(String.format(BUNDESLIGA_DOMAIN + TEAM_STATS_URL, year, round));
		String prevRoundStatsXML = WebCrawler.crawl(prevRoundStatsURL, crawledPages);

		log.debug("Parsing average statistics for round {} year {}", round, year);
		return parseAverageRoundStats(prevRoundStatsXML, parsedDocuments);
	}

	/**
	 * Method which creates Map with pairs {key} => {value}
	 * for different statistics (track distance, sprints, goals, fouls, passes)
	 *
	 * @param prevRoundStatsXML xml containing the previous round statistics
	 * @param parsedDocuments   memorization map for already parsed documents
	 * @return @link Map<String, Double>} with average statistics for given round and year
	 */
	public static Map<String, Integer> parseAverageRoundStats(String prevRoundStatsXML,
			Map<String, Document> parsedDocuments) {

		Document doc = DocumentUtils.parse(prevRoundStatsXML, parsedDocuments);
		NodeList teamNodes = doc.getElementsByTagName(GROUP_STATS_ATTR);

		NamedNodeMap statsAttributes = teamNodes.item(0).getAttributes();
		Map<String, Integer> averageStats = new HashMap<>();

		addAttribute(TRACK_DIST_ATTR, statsAttributes, averageStats);
		addAttribute(TRACK_SPRINTS_ATTR, statsAttributes, averageStats);
		addAttribute(TRACK_PASSES_ATTR, statsAttributes, averageStats);
		addAttribute(TRACK_SHOTS_ATTR, statsAttributes, averageStats);
		addAttribute(TRACK_FOULS_ATTR, statsAttributes, averageStats);

		return averageStats;
	}

	private static void addAttribute(String attrName, NamedNodeMap statsAttributes, Map<String, Integer> averageStats) {
		Node attributeNode = statsAttributes.getNamedItem(attrName);
		String key = attributeNode.getNodeName();
		int value = (int) Double.parseDouble(attributeNode.getNodeValue());
		log.debug("{} : {}", key, value);
		averageStats.put(key, value);
	}

	/**
	 * Method which returns ID from BundesligaName
	 *
	 * @param team team node
	 * @return Bundesliga ID
	 */
	public static String covertIdToTeamNameFromNode(Node team) {
		Node teamMetaDataNode = team.getFirstChild().getNextSibling();
		int teamId = Integer.parseInt(teamMetaDataNode.getAttributes().getNamedItem(TEAM_KEY_ATTR).getNodeValue());
		return TeamsMapping.bundesligaIdToName.get(teamId);
	}

	/**
	 * Return only the match table from XML file. This method using external memorization maps
	 * for already crawled pages and already parsed documents.
	 *
	 * @param year            year for match table
	 * @param round           round for match table
	 * @param crawledPages    memorization map for already crawled pages
	 * @param parsedDocuments memorization map for already parsed documents
	 * @return return match table as NodeList class
	 */
	public static NodeList getMatchTable(int year, int round, Map<URL, String> crawledPages,
			Map<String, Document> parsedDocuments) throws MalformedURLException, InterruptedException {

		String currentRoundMatchesContent = Bundesliga.getMatches(year, round, crawledPages);

		Document currentRoundMatchesXML = DocumentUtils.parse(currentRoundMatchesContent, parsedDocuments);

		return currentRoundMatchesXML.getElementsByTagName(TEAM_ATTR);
	}
}