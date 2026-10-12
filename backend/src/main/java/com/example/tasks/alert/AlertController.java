package com.example.tasks.alert;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

	private final AlertRepository alerts;

	public AlertController(AlertRepository alerts) {
		this.alerts = alerts;
	}

	@GetMapping
	public List<Alert> list(@RequestParam(required = false) Alert.Status status) {
		return status == null ? alerts.findTop200ByOrderByTriggeredAtDescIdDesc()
				: alerts.findTop200ByStatusOrderByTriggeredAtDescIdDesc(status);
	}

	@GetMapping("/summary")
	public Map<String, Long> summary() {
		return Map.of("open", alerts.countByStatus(Alert.Status.OPEN), "acknowledged",
				alerts.countByStatus(Alert.Status.ACKNOWLEDGED));
	}

	@PostMapping("/{id}/acknowledge")
	@Transactional
	public Alert acknowledge(@PathVariable Long id) {
		Alert alert = alerts.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert " + id + " not found"));
		alert.acknowledge();
		return alert;
	}

}
