package org.qora.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;

public class WindowsTimeServiceTests {

	public static void main(String[] args) {
		System.out.println("Detecting Windows Time Service...");
		String[] detectCmd = new String[] { "net", "start" };
		try {
			Process process = new ProcessBuilder(Arrays.asList(detectCmd)).start();
			try (InputStream in = process.getInputStream(); Scanner scanner = new Scanner(in, "UTF8")) {
				scanner.useDelimiter("\\A");
				String output = scanner.hasNext() ? scanner.next() : "";
				boolean isRunning = output.contains("Windows Time");

				System.out.println(String.format("Windows Time Service running: %s", isRunning ? "yes" : "no"));
			}
			int exitStatus = process.waitFor();
			System.out.println(String.format("Exit status: %d", exitStatus));
		} catch (IOException | InterruptedException e) {
			System.err.println(String.format("Failed to detect Windows Time Service: %s", e.getMessage()));
		}

		System.out.println("Starting Windows Time Service...");
		String[] startCmd = new String[] { "net", "start", "w32time" };
		try {
			Process process = new ProcessBuilder(Arrays.asList(startCmd)).start();
			int exitStatus = process.waitFor();
			System.out.println(String.format("Exit status: %d", exitStatus));
		} catch (IOException | InterruptedException e) {
			System.err.println(String.format("Failed to start Windows Time Service: %s", e.getMessage()));
		}

		System.out.println("Force syncing Windows Time Service...");
		String[] resyncCmd = new String[] { "w32tm", "/resync" };
		try {
			Process process = new ProcessBuilder(Arrays.asList(resyncCmd)).start();
			int exitStatus = process.waitFor();
			System.out.println(String.format("Exit status: %d", exitStatus));
		} catch (IOException | InterruptedException e) {
			System.err.println(String.format("Failed to force sync Windows Time Service: %s", e.getMessage()));
		}
	}

}
