package dev.karimbk.reflowtask.dataexport;

import dev.karimbk.reflowtask.user.AuthTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Taking your data out: complete, only your own, and safe to open in a spreadsheet. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExportTests {

	@Autowired
	private MockMvc mvc;

	private Cookie admin;

	@BeforeEach
	void logIn() throws Exception {
		this.admin = new AuthTestSupport(this.mvc).login();
	}

	private void create(Cookie as, String title, String description) throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(as)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"%s","description":"%s","estimatedMinutes":60,"priority":"MEDIUM"}"""
						.formatted(title, description)))
			.andExpect(status().isCreated());
	}

	@Test
	void theJsonExportHoldsEverythingOfYoursAndNothingOfAnyoneElse() throws Exception {
		create(this.admin, "Mine", "notes");
		Cookie member = new AuthTestSupport(this.mvc).memberSession(this.admin, "sam");
		create(member, "Not mine", "private");

		this.mvc.perform(get("/api/v1/export").cookie(this.admin))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("attachment")))
			.andExpect(jsonPath("$.format").value(ExportService.FORMAT))
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(jsonPath("$.settings.workingHours").isArray())
			.andExpect(jsonPath("$.tasks[?(@.title == 'Not mine')]", hasSize(0)))
			.andExpect(jsonPath("$.tasks[?(@.title == 'Mine')].description").value("notes"))
			.andExpect(jsonPath("$.blocks[?(@.taskTitle == 'Mine')]", hasSize(1)))
			.andExpect(jsonPath("$.history").isArray())
			.andExpect(jsonPath("$.calendars").isArray());
	}

	@Test
	void theCsvOpensCleanlyInASpreadsheet() throws Exception {
		create(this.admin, "=HYPERLINK(1)", "a, \\\"quoted\\\" note");

		byte[] body = this.mvc.perform(get("/api/v1/export/tasks.csv").cookie(this.admin))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", containsString("text/csv")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		String csv = new String(body, java.nio.charset.StandardCharsets.UTF_8);

		assertThat(csv).startsWith("﻿id,title,status");
		assertThat(csv).contains(",'=HYPERLINK(1),");
		assertThat(csv).contains(",\"a, \"\"quoted\"\" note\"\r\n");
	}

	@Test
	void cellsAreQuotedOnlyWhenTheyNeedIt() {
		assertThat(ExportService.cell(null)).isEmpty();
		assertThat(ExportService.cell("plain")).isEqualTo("plain");
		assertThat(ExportService.cell(-5)).isEqualTo("-5");
		assertThat(ExportService.cell("-5 minutes")).isEqualTo("'-5 minutes");
		assertThat(ExportService.cell("two\nlines")).isEqualTo("\"two\nlines\"");
	}

}
