package com.example.tasks.ingest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Small HTTP client shared by the connectors that call public data APIs. */
@Component
public class PublicHttp {

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	public String getString(String url) throws IOException, InterruptedException {
		HttpResponse<String> res = client.send(request(url, Duration.ofMinutes(2)), HttpResponse.BodyHandlers.ofString());
		check(url, res);
		return res.body();
	}

	/** Downloads a (possibly very large) file to a temporary path; the caller deletes it. */
	public Path download(String url, String suffix) throws IOException, InterruptedException {
		Path target = Files.createTempFile("ingest-", suffix);
		try {
			HttpResponse<Path> res = client.send(request(url, Duration.ofMinutes(20)),
					HttpResponse.BodyHandlers.ofFile(target));
			check(url, res);
			return target;
		}
		catch (IOException | InterruptedException | RuntimeException e) {
			Files.deleteIfExists(target);
			throw e;
		}
	}

	private static HttpRequest request(String url, Duration timeout) {
		return HttpRequest.newBuilder(URI.create(url))
			.timeout(timeout)
			.header("User-Agent", "vida-monitor/0.1")
			.GET()
			.build();
	}

	private static void check(String url, HttpResponse<?> res) throws IOException {
		if (res.statusCode() != 200) {
			throw new IOException("HTTP " + res.statusCode() + " al consultar " + url);
		}
	}

}
