package dev.karimbk.reflowtask.calendar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import dev.karimbk.reflowtask.common.BadRequestException;

import org.springframework.stereotype.Component;

/**
 * Downloads another calendar's .ics file.
 *
 * The server fetches addresses people type in, so it only speaks http(s), gives up after a short
 * wait, refuses anything larger than a calendar plausibly is, and never shows the response back to
 * anyone - only whether it could be read as a calendar. On a household server behind a VPN that is
 * proportionate; exposed to the internet, fetching arbitrary addresses would need more (see
 * SECURITY.md).
 */
@Component
class CalendarFetcher {

	static final int MAX_BYTES = 5_000_000;

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	/**
	 * webcal:// names no transport. Like calendar apps, try https first and fall back to plain http
	 * only when https cannot connect at all - a server on the home network often has no TLS.
	 */
	String fetch(String address) {
		URI uri = normalize(address);
		if (!isWebcal(address)) {
			return fetch(uri);
		}
		try {
			return download(uri);
		}
		catch (IOException ex) {
			return fetch(URI.create("http" + uri.toString().substring("https".length())));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new BadRequestException("Could not reach that calendar address.");
		}
	}

	private String fetch(URI uri) {
		try {
			return download(uri);
		}
		catch (IOException ex) {
			throw new BadRequestException("Could not reach that calendar address.");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new BadRequestException("Could not reach that calendar address.");
		}
	}

	private String download(URI uri) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(20))
			.header("Accept", "text/calendar, */*;q=0.5")
			.header("User-Agent", "ReflowTask calendar sync")
			.GET()
			.build();
		HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream body = response.body()) {
			if (response.statusCode() != 200) {
				throw new BadRequestException("The calendar address answered with HTTP " + response.statusCode() + ".");
			}
			byte[] bytes = body.readNBytes(MAX_BYTES + 1);
			if (bytes.length > MAX_BYTES) {
				throw new BadRequestException("That calendar is larger than 5 MB.");
			}
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	private static boolean isWebcal(String address) {
		return address.strip().toLowerCase(Locale.ROOT).startsWith("webcal://");
	}

	/** webcal:// is how calendar links are often shared; it is tried as https. Nothing but http(s) is fetched. */
	static URI normalize(String address) {
		String trimmed = address.strip();
		if (isWebcal(trimmed)) {
			trimmed = "https://" + trimmed.substring("webcal://".length());
		}
		try {
			URI uri = new URI(trimmed);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
				throw new BadRequestException("A calendar address must start with https://, http:// or webcal://.");
			}
			return uri;
		}
		catch (URISyntaxException ex) {
			throw new BadRequestException("That is not a valid address.");
		}
	}

}
