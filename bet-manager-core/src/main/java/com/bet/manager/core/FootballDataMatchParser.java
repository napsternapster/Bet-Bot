package com.bet.manager.core;

import com.bet.manager.commons.ResultMessages;
import com.bet.manager.model.entity.FootballMatch;
import com.bet.manager.model.entity.MatchStatus;
import com.bet.manager.model.exceptions.MatchStatusNotExist;
import com.bet.manager.model.util.FootballMatchBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FootballDataMatchParser implements IMatchParser {

	private static final Logger LOG = LoggerFactory.getLogger(FootballDataMatchParser.class);

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private static final String FINISHED_TAG = "FINISHED";
	private static final String PROCESS_TAG = "IN_PLAY";
	private static final String NOT_STARTED_TAG = "SCHEDULED";
	private static final String POSTPONED_TAG = "POSTPONED";
	private static final String TIMED_TAG = "TIMED";
	private static final String CANCELED_TAG = "CANCELED";

	@Override
	public Map<MatchStatus, List<FootballMatch>> parse(String content) {

		Map<MatchStatus, List<FootballMatch>> matches = initMatchesStructure();

		JSONObject matchesAsJSON = new JSONObject(content);

		for (Object matchObject : (JSONArray) matchesAsJSON.get("fixtures")) {

			try {

				LocalDateTime startDate = LocalDateTime.parse(getProperty(matchObject, "date"), DATE_FORMATTER);

				String homeTeam = convertToBundesligaTeam(getProperty(matchObject, "homeTeamName"));
				String awayTeam = convertToBundesligaTeam(getProperty(matchObject, "awayTeamName"));

				MatchStatus status = parseStatus(getProperty(matchObject, "status"));

				Integer homeTeamGoals = -1;
				Integer awayTeamGoals = -1;

				if (status.equals(MatchStatus.FINISHED) || status.equals(MatchStatus.STARTED)) {
					JSONObject result = (JSONObject) ((JSONObject) matchObject).get("result");
					homeTeamGoals =
							result.has("goalsHomeTeam") ? Integer.parseInt(result.get("goalsHomeTeam").toString()) : -1;
					awayTeamGoals =
							result.has("goalsAwayTeam") ? Integer.parseInt(result.get("goalsAwayTeam").toString()) : -1;
				}

				Integer matchDay = Integer.parseInt(getProperty(matchObject, "matchday"));

				if (matchDay < 2) {
					LOG.warn("Found match for round 1.. skipping..");
					continue;
				}

				FootballMatch match = new FootballMatchBuilder()
						.setHomeTeamName(homeTeam)
						.setAwayTeamName(awayTeam)
						.setStatus(status)
						.setStartDate(startDate)
						.setRound(matchDay)
						.setYear(startDate.getYear())
						.setResult(homeTeamGoals == -1 || awayTeamGoals == -1 ? ResultMessages.UNKNOWN_RESULT :
								String.format("%s-%s", homeTeamGoals, awayTeamGoals))
						.build();

				matches.get(match.getMatchStatus()).add(match);

			} catch (Exception e) {
				LOG.error("Error occur during fetching fixture matches..", e);
			}
		}

		return matches;
	}

	private Map<MatchStatus, List<FootballMatch>> initMatchesStructure() {

		Map<MatchStatus, List<FootballMatch>> matches = new HashMap<>();
		Arrays.stream(MatchStatus.values()).forEach(status -> matches.put(status, new ArrayList<>()));
		return matches;
	}

	private String getProperty(Object jsonObject, String property) {
		return ((JSONObject) jsonObject).get(property).toString();
	}

	private String convertToBundesligaTeam(String footballDataMatchName) {
		if (TeamsMapping.footballDataToBundesliga.containsKey(footballDataMatchName))
			return TeamsMapping.footballDataToBundesliga.get(footballDataMatchName);

		throw new IllegalArgumentException("Cannot map football data team [" + footballDataMatchName + "] to bundesliga team");
	}

	private MatchStatus parseStatus(String status) {
		switch (status) {
		case FINISHED_TAG:
			return MatchStatus.FINISHED;
		case PROCESS_TAG:
			return MatchStatus.STARTED;
		case NOT_STARTED_TAG:
		case POSTPONED_TAG:
		case CANCELED_TAG:
		case TIMED_TAG:
			return MatchStatus.NOT_STARTED;
		}

		throw new MatchStatusNotExist(String.format("Match status %s is not correct", status));
	}
}
