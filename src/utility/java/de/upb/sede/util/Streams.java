package de.upb.sede.util;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Streams {

	/**
	 * Reads the content in the given input stream chunkwise and fills it into a byte array output stream.
	 */
	public static ByteArrayOutputStream InReadChunked(InputStream is) {

		try (InputStream inputStream = is) {

			ByteArrayOutputStream result = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int length;
			while ((length = inputStream.read(buffer)) != -1) {
				result.write(buffer, 0, length);
			}
			return result;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Reads the content in the given input stream and fills it into a byte array.
	 */
	public static byte[] InReadByteArr(InputStream is) {
		return InReadChunked(is).toByteArray();
	}

	/**
	 * Reads the content in the given input stream and converts it to a string.
	 */
	public static String InReadString(InputStream is) {
		try {
			return InReadChunked(is).toString(StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Writes the given content into the given outputstream. if close is true,
	 * closes the stream afterwards.
	 */
	public static void OutWriteString(OutputStream outstream, String content, boolean close) {

		try {
			outstream.write(content.getBytes(StandardCharsets.UTF_8.name()));
			if (close) {
				outstream.close();
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Reads a line of characters out of the given input stream.
	 * @return the read string
	 */
	public static String InReadLine(InputStream in) {
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		try {
			return br.readLine();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static String errToString(Exception ex) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		String sStackTrace = sw.toString();
		return sStackTrace;
	}
}
