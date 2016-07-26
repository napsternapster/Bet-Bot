package com.bet.manager.web;

import com.bet.manager.model.PredictionsInfo;
import com.bet.manager.services.PredictionInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/predictions", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class PredictionInfoRestController {

	@Autowired
	private PredictionInfoService predictionInfoService;

	@ResponseBody
	@RequestMapping(value = "/info", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public PredictionsInfo getPredictionsInfo() {
		return predictionInfoService.getPredictionsInfo();
	}
}
