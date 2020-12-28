package fr.upem.net.tcp.parsing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Parsing {

	public static List<String> parsing(String file) {
		Path path = Paths.get(Objects.requireNonNull(file));
		List<String> list = new ArrayList<>();

		try (Stream<String> lines = Files.lines(path)) {
			lines.forEach(list::add);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return list;
	}
}