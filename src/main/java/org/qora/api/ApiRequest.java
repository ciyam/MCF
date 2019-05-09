package org.qora.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.exceptions.XMLMarshalException;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;

public class ApiRequest {

	public static String perform(String uri, Map<String, String> params) {
		if (params != null && !params.isEmpty())
			uri += "?" + getParamsString(params);

		InputStream in = fetchStream(uri);
		if (in == null)
			return null;

		try (Scanner scanner = new Scanner(in, "UTF8")) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				// We tried...
			}
		}
	}

	public static Object perform(String uri, Class<?> responseClass, Map<String, String> params) {
		Unmarshaller unmarshaller = createUnmarshaller(responseClass);

		if (params != null && !params.isEmpty())
			uri += "?" + getParamsString(params);

		InputStream in = fetchStream(uri);
		if (in == null)
			return null;

		try {
			StreamSource json = new StreamSource(in);

			// Attempt to unmarshal JSON stream to Settings
			return unmarshaller.unmarshal(json, responseClass).getValue();
		} catch (UnmarshalException e) {
			Throwable linkedException = e.getLinkedException();
			if (linkedException instanceof XMLMarshalException) {
				String message = ((XMLMarshalException) linkedException).getInternalException().getLocalizedMessage();
				throw new RuntimeException(message);
			}

			throw new RuntimeException("Unable to unmarshall API response", e);
		} catch (JAXBException e) {
			throw new RuntimeException("Unable to unmarshall API response", e);
		}
	}

	private static Unmarshaller createUnmarshaller(Class<?> responseClass) {
		try {
			// Create JAXB context aware of Settings
			JAXBContext jc = JAXBContextFactory.createContext(new Class[] { responseClass }, null);

			// Create unmarshaller
			Unmarshaller unmarshaller = jc.createUnmarshaller();

			// Set the unmarshaller media type to JSON
			unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");

			// Tell unmarshaller that there's no JSON root element in the JSON input
			unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);

			return unmarshaller;
		} catch (JAXBException e) {
			throw new RuntimeException("Unable to create API unmarshaller", e);
		}
	}

	public static String getParamsString(Map<String, String> params) {
		StringBuilder result = new StringBuilder();

		try {
			for (Map.Entry<String, String> entry : params.entrySet()) {
				result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
				result.append("=");
				result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
				result.append("&");
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Cannot encode API request params", e);
		}

		String resultString = result.toString();
		return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
	}

	public static InputStream fetchStream(String uri) {
		try {
			URL url = new URL(uri);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			try {
				con.setRequestMethod("GET");
				con.setConnectTimeout(5000);
				con.setReadTimeout(5000);
				ApiRequest.setConnectionSSL(con);

				try {
					int status = con.getResponseCode();

					if (status != 200)
						return null;
				} catch (IOException e) {
					return null;
				}

				return con.getInputStream();
			} catch (IOException e) {
				return null;
			}
		} catch (MalformedURLException e) {
			throw new RuntimeException("Malformed API request", e);
		} catch (IOException e) {
			// Temporary fail
			return null;
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
