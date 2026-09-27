package dev.karimbk.reflowtask.dataexport;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import dev.karimbk.reflowtask.dataexport.ExportService.Export;
import dev.karimbk.reflowtask.user.CurrentUser;
import dev.karimbk.reflowtask.user.User;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Your own data as files: a complete JSON export, and the task list as CSV for a spreadsheet. */
@RestController
@RequestMapping("/api/v1/export")
class ExportController {

	private final ExportService service;

	ExportController(ExportService service) {
		this.service = service;
	}

	@GetMapping
	ResponseEntity<Export> all(@CurrentUser User user) {
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, attachment(user, "json"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(this.service.export(user.getId(), user.getUsername()));
	}

	@GetMapping("/tasks.csv")
	ResponseEntity<String> tasks(@CurrentUser User user) {
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, attachment(user, "csv"))
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.body(this.service.tasksCsv(user.getId()));
	}

	private static String attachment(User user, String extension) {
		return ContentDisposition.attachment()
			.filename("reflowtask-" + user.getUsername() + "-" + LocalDate.now() + "." + extension, StandardCharsets.UTF_8)
			.build()
			.toString();
	}

}
