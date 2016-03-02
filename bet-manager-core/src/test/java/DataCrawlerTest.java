import com.bet.manager.commons.util.ClasspathUtils;
import com.bet.manager.core.DataCrawler;
import com.bet.manager.core.DocumentUtils;
import org.junit.Test;
import org.testng.Assert;
import org.w3c.dom.Document;

import java.util.HashMap;
import java.util.Map;

public class DataCrawlerTest {

	@Test
	public void testCorrectRankingParsing() {

		Document doc = DocumentUtils.parse(ClasspathUtils.getContentUTF8("crawl-data/bundesliga-round.xml"));
		Map<String, Integer> actual = DataCrawler.getRoundRanking(doc);

		Map<String, Integer> expected = new HashMap<>();
		expected.put("VfB Stuttgart", 1);
		expected.put("VfL Wolfsburg", 2);
		expected.put("Borussia Dortmund", 3);
		expected.put("1.FSV Mainz 05", 4);
		expected.put("SV Werder Bremen", 5);
		expected.put("Hannover 96", 6);
		expected.put("1.FC Nürnberg", 7);
		expected.put("Borussia M'gladbach", 8);
		expected.put("FC Augsburg", 9);
		expected.put("SC Freiburg", 10);
		expected.put("1899 Hoffenheim", 11);
		expected.put("FC Bayern München", 12);
		expected.put("Hertha BSC", 13);
		expected.put("Hamburger SV", 14);
		expected.put("1.FC Kaiserslautern", 15);
		expected.put("Bayer 04 Leverkusen", 16);
		expected.put("1.FC Köln", 17);
		expected.put("FC Schalke 04", 18);

		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testCorrectParsingPreviousRoundStatsForTeams() {

		Map<String, Double> actual =
				DataCrawler
						.getAverageRoundStats(ClasspathUtils.getContentUTF8("crawl-data/bundesliga-round-stats.xml"));

		Map<String, Double> expected = new HashMap<>();

		expected.put("imp:tracking-distance", 111923.72);
		expected.put("imp:tracking-sprints", 191.69);
		expected.put("imp:passes-total", 393.78);
		expected.put("shots-total", 12.54);
		expected.put("fouls-committed", 14.94);

		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testCorrectGettingTheFirstRoundOpponent() {

		String content = ClasspathUtils.getContentUTF8("crawl-data/resultdb-last-matches-for-team.html");
		String[] actual = DataCrawler.getCurrentTeamOpponentAndVenue(content, 2);
		String[] expected = new String[] { "Mönchengladbach", "-1" };
		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testCorrectGettingThirdRoundOpponent() {

		String content = ClasspathUtils.getContentUTF8("crawl-data/resultdb-last-matches-for-team.html");
		String[] actual = DataCrawler.getCurrentTeamOpponentAndVenue(content, 3);
		String[] expected = new String[] { "Bayer Leverkusen", "1" };
		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testCorrectGettingLastFiveMatches() {

		String content = ClasspathUtils.getContentUTF8("crawl-data/resultdb-last-matches-for-team.html");
		String actual = DataCrawler.getResultsForPastFiveGames(content, 6);
		String expected = "2 0 0 2 1";
		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testCorrectGettingFiveLastMatchesButContainsOnlyThree() {

		String content = ClasspathUtils.getContentUTF8("crawl-data/resultdb-last-matches-for-team.html");
		String actual = DataCrawler.getResultsForPastFiveGames(content, 4);
		String expected = "1 0 0 1 1";
		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testCorrectGettingFiveLastMatchesForRound34() {

		String content = ClasspathUtils.getContentUTF8("crawl-data/resultdb-last-matches-for-team.html");
		String actual = DataCrawler.getResultsForPastFiveGames(content, 34);
		String expected = "3 1 0 0 1";
		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testCorrectParsingResultToNormalizationArrayForWin() {

		int[] normalizationArray = new int[5];
		String result = "W";
		String score = "1-0";
		DataCrawler.addMatchToNormalizationArray(result, score, normalizationArray);
		Assert.assertEquals(normalizationArray[2], 1);
	}

	@Test
	public void testCorrectParsingResultToNormalizationArrayForTie() {

		int[] normalizationArray = new int[5];
		String result = "D";
		String score = "1-1";
		DataCrawler.addMatchToNormalizationArray(result, score, normalizationArray);
		Assert.assertEquals(normalizationArray[4], 1);
	}

	@Test
	public void testCorrectParsingResultToNormalizationArrayForHugeWin() {

		int[] normalizationArray = new int[5];
		String result = "W";
		String score = "3-0";
		DataCrawler.addMatchToNormalizationArray(result, score, normalizationArray);
		Assert.assertEquals(normalizationArray[0], 1);
	}

	@Test
	public void testCorrectParsingTwoResultToNormalizationArrayForHugeWin() {

		int[] normalizationArray = new int[5];
		String result = "W";
		String score = "3-0";
		DataCrawler.addMatchToNormalizationArray(result, score, normalizationArray);
		DataCrawler.addMatchToNormalizationArray(result, score, normalizationArray);
		Assert.assertEquals(normalizationArray[0], 2);
	}

	@Test
	public void testCorrectStatsForMatch() {

		String prevRoundTeamStatsXML = ClasspathUtils.getContentUTF8("crawl-data/bundesliga-stats-for-matches.xml");

		Map<String, Double> prevRoundStats = new HashMap<>();
		prevRoundStats.put("imp:tracking-distance", 111923.72);
		prevRoundStats.put("imp:tracking-sprints", 191.69);
		prevRoundStats.put("imp:passes-total", 393.78);
		prevRoundStats.put("shots-total", 12.54);
		prevRoundStats.put("fouls-committed", 14.94);

		String actual =
				DataCrawler.getPrevRoundTeamPerformance(prevRoundTeamStatsXML, "1.FSV Mainz 05", prevRoundStats);
		String expected = "125042.44 184 320 13 11";

		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testCorrectStatsForMatchWhichHaveEmptyDistanceAttributeAndShouldGetFromThePrevRound() {

		String prevRoundTeamStatsXML = ClasspathUtils.getContentUTF8("crawl-data/bundesliga-stats-for-matches.xml");

		Map<String, Double> prevRoundStats = new HashMap<>();
		prevRoundStats.put("imp:tracking-distance", 111923.72);
		prevRoundStats.put("imp:tracking-sprints", 191.69);
		prevRoundStats.put("imp:passes-total", 393.78);
		prevRoundStats.put("shots-total", 12.54);
		prevRoundStats.put("fouls-committed", 14.94);

		String actual =
				DataCrawler.getPrevRoundTeamPerformance(prevRoundTeamStatsXML, "FC Bayern München", prevRoundStats);
		String expected = "111923.72 189 579 21 15";

		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testRankingStatsForTeamForRound1() {

		String content = ClasspathUtils.getContentUTF8("crawl-data/bundesliga-stats-for-matches.xml");
		Document doc = DocumentUtils.parse(content);

		String actual = DataCrawler.getCurrentRankingStats("FC Bayern München", doc);
		String expected = "0 -1";

		Assert.assertEquals(actual, expected);
	}

	@Test
	public void testRankingStatsForTeamForRound15() {

		String content = ClasspathUtils.getContentUTF8("crawl-data/bundesliga-stats-for-matches-round-15.xml");
		Document doc = DocumentUtils.parse(content);

		String actual = DataCrawler.getCurrentRankingStats("Borussia M'gladbach", doc);
		String expected = "14 30";

		Assert.assertEquals(actual, expected);
	}
}
