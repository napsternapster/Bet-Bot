package com.bet.manager.commons.util;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class ResourceUtils {

	public static String getContent(String path, Class c) {

		String content;

		try (InputStream is = new BufferedInputStream(c.getClassLoader().getResourceAsStream(path))) {
			content = IOUtils.toString(is, Charset.forName("UTF-8"));
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read the resource " + path);
		}

		return content;
	}
}
