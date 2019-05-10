package org.qora;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.Collections;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

public class HttpsTest {

	public static void main(String argv[]) throws IOException {
		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		final String uri = "https://github.com/bcgit/bc-java/raw/02d0a89fed488ca65de08afc955dfe7432af5f50/libs/activation.jar";

		InputStream in = fetchStream(uri);
		if (in == null) {
			System.err.println(String.format("Failed to fetch from %s", uri));
			return;
		}

		Path tmpPath = null;
		try {
			// Save input stream into temporary file
			tmpPath = Files.createTempFile(null, null);
			Files.copy(in, tmpPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			System.err.println(String.format("Failed to save %s", uri));
		} finally {
			if (tmpPath != null)
				try {
					Files.deleteIfExists(tmpPath);
				} catch (IOException e) {
					// We tried...
				}
		}
	}

	public static InputStream fetchStream(String uri) throws IOException {
		try {
			URL url = new URL(uri);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			con.setRequestMethod("GET");
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);
			setConnectionSSL(con);

			int status = con.getResponseCode();

			if (status != 200)
				return null;

			return con.getInputStream();
		} catch (MalformedURLException e) {
			throw new RuntimeException("Malformed API request", e);
		}
	}

	public static void setConnectionSSL(HttpURLConnection con) {
		if (!(con instanceof HttpsURLConnection))
			return;

		HttpsURLConnection httpsCon = (HttpsURLConnection) con;
		URL url = con.getURL();

		httpsCon.setSSLSocketFactory(new org.bouncycastle.jsse.util.CustomSSLSocketFactory(httpsCon.getSSLSocketFactory()) {
			@Override
			protected Socket configureSocket(Socket s) {
				if (s instanceof SSLSocket) {
					SSLSocket ssl = (SSLSocket) s;

					SNIHostName sniHostName = new SNIHostName(url.getHost());
					if (null != sniHostName) {
						SSLParameters sslParameters = new SSLParameters();

						sslParameters.setServerNames(Collections.<SNIServerName>singletonList(sniHostName));
						ssl.setSSLParameters(sslParameters);
					}
				}

				return s;
			}
		});
	}

}
