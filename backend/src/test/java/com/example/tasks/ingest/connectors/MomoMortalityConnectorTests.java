package com.example.tasks.ingest.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.tasks.ingest.SeriesInfo;
import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MomoMortalityConnectorTests {

	private static final String CSV = """
			"ambito","cod_ambito","cod_ine_ambito","nombre_ambito","cod_sexo","nombre_sexo","cod_gedad","nombre_gedad","fecha_defuncion","defunciones_observadas","defunciones_estimadas_base","defunciones_estimadas_base_q01","defunciones_estimadas_base_q99","defunciones_atrib_exc_temp","defunciones_atrib_def_temp"
			"nacional",,,,"all","todos","all","todos","2026-09-01",1100,1000,1026,1180,12.57,0
			"nacional",,,,"all","todos","all","todos","2026-09-02",1300,1000,1025,1180,10.45,0
			"provincia","CU",16,"Cuenca","6","mujeres","0-14","edad 0-14","2015-01-01",,0.01,0,0,0,0
			"ccaa","IB",4,"Balears, Illes","all","todos","all","todos","2026-09-01",20,25,3,17,0,0
			""";

	@TempDir
	Path tmp;

	@Test
	void landsRawCsvAndDerivesRollingExcess() throws Exception {
		Path csv = tmp.resolve("momo.csv");
		Files.writeString(csv, CSV);
		Warehouse warehouse = new Warehouse("jdbc:duckdb:");
		MomoMortalityConnector connector = new MomoMortalityConnector(null, warehouse);

		connector.load(csv);

		List<Point> excess = warehouse.points("momo.es.exceso7d.total", LocalDate.of(2000, 1, 1));
		assertThat(excess).hasSize(2);
		// day 1: 1100/1000; day 2 (rolling): 2400/2000
		assertThat(excess.get(0).value()).isCloseTo(10.0, within(1e-9));
		assertThat(excess.get(1).value()).isCloseTo(20.0, within(1e-9));
		assertThat(warehouse.points("momo.ib.exceso7d.total", LocalDate.of(2000, 1, 1)).getFirst().value())
			.isCloseTo(-20.0, within(1e-9));
		assertThat(warehouse.points("momo.es.calor", LocalDate.of(2000, 1, 1))).hasSize(2);
		assertThat(connector.seriesInfo()).extracting(SeriesInfo::name)
			.contains("Exceso de mortalidad 7d · Balears, Illes");
	}

}
