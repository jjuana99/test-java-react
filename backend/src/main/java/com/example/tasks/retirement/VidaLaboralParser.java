package com.example.tasks.retirement;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Reads the Seguridad Social "Informe de vida laboral" PDF. The PDF is processed in memory
 * and never stored or logged.
 */
@Component
public class VidaLaboralParser {

	private static final DateTimeFormatter DOTTED = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private static final Map<String, Integer> MONTHS = Map.ofEntries(Map.entry("enero", 1), Map.entry("febrero", 2),
			Map.entry("marzo", 3), Map.entry("abril", 4), Map.entry("mayo", 5), Map.entry("junio", 6),
			Map.entry("julio", 7), Map.entry("agosto", 8), Map.entry("septiembre", 9), Map.entry("setiembre", 9),
			Map.entry("octubre", 10), Map.entry("noviembre", 11), Map.entry("diciembre", 12));

	private static final Pattern REPORT_DATE = Pattern
		.compile("al d[ií]a (\\d{1,2}) de ([a-záéíóú]+) de (\\d{4})", Pattern.CASE_INSENSITIVE);

	/** Summary block between "durante un total de" and the start of the situations list. */
	private static final Pattern SUMMARY = Pattern.compile("durante un total de(.{0,200}?)Presenta",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern YEARS = Pattern.compile("(\\d+)\\s*A[ñn]os", Pattern.CASE_INSENSITIVE);

	private static final Pattern MONTHS_COUNT = Pattern.compile("(\\d+)\\s*mes(?:es)?", Pattern.CASE_INSENSITIVE);

	private static final Pattern DAYS = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})*|\\d+)\\s*d[ií]as?",
			Pattern.CASE_INSENSITIVE);

	/** A situation row: regime, account code, name, alta, efecto alta, baja, ..., días. */
	private static final Pattern ROW = Pattern.compile("^([A-ZÁÉÍÓÚÑ.]+)\\s+(\\S+)\\s+(.*?)\\s+"
			+ "(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s+"
			+ "(\\d{2}\\.\\d{2}\\.\\d{4}|-{3})\\s+.*?(\\d{1,3}(?:\\.\\d{3})*|\\d+)\\s*$");

	public WorkHistory parse(byte[] pdf) throws IOException {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			return parseText(stripper.getText(doc));
		}
	}

	WorkHistory parseText(String text) {
		if (!text.toUpperCase(Locale.ROOT).contains("VIDA LABORAL")) {
			throw new IllegalArgumentException("El PDF no parece un informe de vida laboral de la Seguridad Social");
		}
		List<String> warnings = new ArrayList<>();
		LocalDate reportDate = reportDate(text);

		int years = 0;
		int months = 0;
		int days = 0;
		int totalDays = 0;
		Matcher summary = SUMMARY.matcher(text);
		if (summary.find()) {
			String block = summary.group(1);
			years = firstInt(YEARS, block);
			months = firstInt(MONTHS_COUNT, block);
			// the block holds both the total in days and the leftover days of the y/m/d breakdown
			List<Integer> dayValues = new ArrayList<>();
			for (Matcher m = DAYS.matcher(block); m.find();) {
				dayValues.add(Integer.parseInt(m.group(1).replace(".", "")));
			}
			if (!dayValues.isEmpty()) {
				totalDays = dayValues.stream().max(Integer::compare).orElseThrow();
				days = dayValues.size() > 1 ? dayValues.stream().min(Integer::compare).orElseThrow() : 0;
			}
		}
		else {
			warnings.add("No se ha encontrado el total de días en alta; revisa los años cotizados a mano.");
		}

		List<WorkHistory.Period> periods = new ArrayList<>();
		for (String line : text.split("\\R")) {
			Matcher m = ROW.matcher(line.strip());
			if (m.matches()) {
				String employer = m.group(3).strip();
				boolean assimilated = m.group(2).startsWith("---")
						|| employer.matches("(?i).*(DESEMPLEO|EXCEDENCIA|CONVENIO ESPECIAL|VACACIONES).*");
				periods.add(new WorkHistory.Period(m.group(1), employer, LocalDate.parse(m.group(4), DOTTED),
						m.group(6).startsWith("-") ? null : LocalDate.parse(m.group(6), DOTTED),
						Integer.parseInt(m.group(7).replace(".", "")), assimilated));
			}
		}
		if (periods.isEmpty()) {
			warnings.add("No se han podido leer las situaciones del informe.");
		}

		double contributionYears = totalDays > 0 ? totalDays / 365.0 : years + months / 12.0 + days / 365.0;
		LocalDate first = periods.stream().map(WorkHistory.Period::from).min(Comparator.naturalOrder()).orElse(null);
		WorkHistory.Period open = periods.stream()
			.filter(p -> p.to() == null)
			.max(Comparator.comparing(WorkHistory.Period::from))
			.orElse(null);
		warnings.add("El informe cuenta días en alta, que pueden no coincidir exactamente con los días cotizados "
				+ "(p. ej. tiempo parcial o pluriempleo). No incluye clases pasivas ni periodos en el extranjero.");
		if (reportDate != null && reportDate.isBefore(LocalDate.now().minusMonths(6))) {
			warnings.add("El informe tiene más de 6 meses: si sigues de alta sumamos el tiempo transcurrido, pero conviene descargar uno reciente.");
		}
		return new WorkHistory(reportDate, totalDays, years, months, days, Math.round(contributionYears * 100) / 100.0,
				first, open == null ? null : open.regime(), open != null && !open.assimilated(), address(text),
				periods, warnings);
	}

	/** Header text "domicilio en ... ha figurado": the home address, or null when absent or redacted. */
	private static final Pattern ADDRESS = Pattern.compile("domicilio en(.{0,200}?)ha figurado",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	static String address(String text) {
		Matcher m = ADDRESS.matcher(text);
		if (!m.find()) {
			return null;
		}
		String address = m.group(1).replaceAll("\\s+", " ").replaceAll("^[\\s,.;:]+|[\\s,.;:]+$", "");
		// a real address has letters and a number (street number or postal code)
		return address.length() >= 6 && address.matches(".*\\p{L}.*") && address.matches(".*\\d.*") ? address : null;
	}

	private static LocalDate reportDate(String text) {
		Matcher m = REPORT_DATE.matcher(text);
		if (!m.find()) {
			return null;
		}
		Integer month = MONTHS.get(m.group(2).toLowerCase(Locale.ROOT));
		return month == null ? null
				: LocalDate.of(Integer.parseInt(m.group(3)), Objects.requireNonNull(month),
						Integer.parseInt(m.group(1)));
	}

	private static int firstInt(Pattern p, String s) {
		Matcher m = p.matcher(s);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

}
