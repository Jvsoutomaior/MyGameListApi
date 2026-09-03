package com.mygamelist.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mygamelist.api.entity.Game;
import com.mygamelist.api.entity.GameStatus;
import com.mygamelist.api.entity.UserAccount;
import com.mygamelist.api.repository.GameListEntryRepository;
import com.mygamelist.api.repository.GameRepository;
import com.mygamelist.api.repository.UserAccountRepository;
import com.mygamelist.api.service.GameListService;
import com.mygamelist.api.service.UserAccountService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MvpApiIntegrationTests {

  private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private UserAccountService userAccountService;

  @Autowired private GameListService gameListService;

  @Autowired private UserAccountRepository userAccountRepository;

  @Autowired private GameRepository gameRepository;

  @Autowired private GameListEntryRepository gameListEntryRepository;

  private UserAccount alice;
  private UserAccount bob;
  private Game witcher;
  private Game portal;
  private Game hades;

  @BeforeEach
  void setUp() {
    gameListEntryRepository.deleteAll();
    userAccountRepository.deleteAll();
    gameRepository.deleteAll();

    alice = userAccountService.register("alice", "alice@example.com", "alice password");
    bob = userAccountService.register("bob", "bob@example.com", "bob password");
    witcher = gameRepository.save(new Game("TEST", "witcher-3", "The Witcher 3", null, null, NOW));
    portal = gameRepository.save(new Game("TEST", "portal-2", "Portal 2", null, null, NOW));
    hades = gameRepository.save(new Game("TEST", "hades", "Hades", null, null, NOW));
  }

  @Test
  void exposesPublicCatalogProfileAndList() throws Exception {
    gameListService.add(bob.getId(), portal.getId(), GameStatus.COMPLETED, 10, "Excellent");

    mockMvc
        .perform(
            get("/api/games").param("query", "the witcher").param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("The Witcher 3"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(get("/api/games/{gameId}", hades.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Hades"));

    mockMvc
        .perform(get("/api/users/bob"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("bob"))
        .andExpect(jsonPath("$.email").doesNotExist());

    mockMvc
        .perform(get("/api/users/bob/games"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].game.title").value("Portal 2"))
        .andExpect(jsonPath("$.content[0].score").value(10));
  }

  @Test
  void managesOnlyAuthenticatedUsersOwnList() throws Exception {
    gameListService.add(bob.getId(), portal.getId(), GameStatus.PLAYING, null, null);
    MockHttpSession aliceSession = login("alice@example.com", "alice password");

    mockMvc
        .perform(
            post("/api/me/games")
                .session(aliceSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "gameId": "%s",
                      "status": "PLAYING",
                      "score": 8,
                      "comment": "Great so far"
                    }
                    """
                        .formatted(witcher.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.game.title").value("The Witcher 3"))
        .andExpect(jsonPath("$.status").value("PLAYING"));

    mockMvc
        .perform(get("/api/me/games").session(aliceSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].game.id").value(witcher.getId().toString()));

    mockMvc
        .perform(
            patch("/api/me/games/{gameId}", portal.getId())
                .session(aliceSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status":"DROPPED"}
                    """))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            patch("/api/me/games/{gameId}", witcher.getId())
                .session(aliceSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status":"COMPLETED","score":9,"comment":"Finished"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.score").value(9));

    mockMvc
        .perform(
            delete("/api/me/games/{gameId}", witcher.getId()).session(aliceSession).with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/me/games").session(aliceSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  void exposesCorsAndOpenApiDocumentation() throws Exception {
    mockMvc
        .perform(
            options("/api/games")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("MyGameList API"))
        .andExpect(jsonPath("$.paths['/api/games']").exists())
        .andExpect(jsonPath("$.paths['/api/me/games']").exists());
  }

  private MockHttpSession login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                            .formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }
}
