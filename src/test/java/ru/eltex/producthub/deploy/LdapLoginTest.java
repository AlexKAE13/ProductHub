package ru.eltex.producthub.deploy;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;

/** Вход через LDAP (SPEC.md этап 7): пароль проверяет каталог, роли берутся из групп, пользователь заводится при первом входе. */
@SpringBootTest(properties = {
        "producthub.ldap.enabled=true",
        "producthub.ldap.base=dc=example,dc=local",
        "producthub.ldap.user-search-base=ou=people",
        "producthub.ldap.user-search-filter=(uid={0})",
        "producthub.ldap.group-search-base=ou=groups",
        "producthub.ldap.group-roles.hub-product=PRODUCT",
        "producthub.ldap.group-roles.hub-sales=SALES",
        "producthub.ldap.group-roles.hub-head=HEAD"})
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class LdapLoginTest {

    static InMemoryDirectoryServer server;

    @BeforeAll
    static void startDirectory() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=local");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("test", 0));
        config.setSchema(null);
        server = new InMemoryDirectoryServer(config);
        server.add("dn: dc=example,dc=local", "objectClass: top", "objectClass: domain", "dc: example");
        server.add("dn: ou=people,dc=example,dc=local", "objectClass: top", "objectClass: organizationalUnit", "ou: people");
        server.add("dn: ou=groups,dc=example,dc=local", "objectClass: top", "objectClass: organizationalUnit", "ou: groups");
        person("anna", "Анна Тестова", "anna@example.local", "secret1");
        person("boris", "Борис Продавцов", "boris@example.local", "secret2");
        person("nogroup", "Без Группы", "nogroup@example.local", "secret3");
        person("kuznetsov", "Александр Кузнецов", "kuznetsov@example.local", "secret4");
        group("hub-product", "anna", "kuznetsov");
        group("hub-sales", "boris");
        group("other-group", "nogroup");
        server.startListening();
    }

    private static void person(String uid, String name, String mail, String password) throws Exception {
        server.add("dn: uid=" + uid + ",ou=people,dc=example,dc=local", "objectClass: top", "objectClass: person",
                "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: " + uid, "cn: " + name, "sn: " + uid,
                "mail: " + mail, "userPassword: " + password);
    }

    private static void group(String cn, String... members) throws Exception {
        String[] lines = new String[4 + members.length];
        lines[0] = "dn: cn=" + cn + ",ou=groups,dc=example,dc=local";
        lines[1] = "objectClass: top";
        lines[2] = "objectClass: groupOfNames";
        lines[3] = "cn: " + cn;
        for (int i = 0; i < members.length; i++) {
            lines[4 + i] = "member: uid=" + members[i] + ",ou=people,dc=example,dc=local";
        }
        server.add(lines);
    }

    @AfterAll
    static void stopDirectory() {
        server.shutDown(true);
    }

    @DynamicPropertySource
    static void ldapUrl(DynamicPropertyRegistry registry) throws Exception {
        if (server == null) {
            startDirectory();
        }
        registry.add("producthub.ldap.url", () -> "ldap://localhost:" + server.getListenPort());
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void anonymousIsSentToLoginPageWithPasswordForm() throws Exception {
        mvc.perform(get("/catalog")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    void correctPasswordLogsInAndCreatesUserWithRolesFromGroups() throws Exception {
        mvc.perform(formLogin("/login").user("anna").password("secret1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername("anna"))
                .andExpect(authenticated().withRoles("PRODUCT"));
        assertEquals("Анна Тестова", jdbc.queryForObject("select full_name from app_user where login = 'anna'", String.class));
        assertEquals("anna@example.local", jdbc.queryForObject("select email from app_user where login = 'anna'", String.class));
        assertEquals(List.of("PRODUCT"), jdbc.queryForList("select r.role from app_user_role r join app_user u on u.id = r.user_id"
                + " where u.login = 'anna'", String.class));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/login").user("anna").password("не тот"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void userWithoutHubGroupCannotEnter() throws Exception {
        mvc.perform(formLogin("/login").user("nogroup").password("secret3"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
        assertEquals(0, jdbc.queryForObject("select count(*) from app_user where login = 'nogroup'", Integer.class),
                "без группы пользователь в базе не появляется");
    }

    @Test
    void unknownUserIsRejected() throws Exception {
        mvc.perform(formLogin("/login").user("nobody").password("secret1")).andExpect(unauthenticated());
    }

    @Test
    void rolesFromGroupsDecideWhatIsAllowed() throws Exception {
        MockHttpSession product = (MockHttpSession) mvc.perform(formLogin("/login").user("anna").password("secret1"))
                .andReturn().getRequest().getSession();
        MockHttpSession sales = (MockHttpSession) mvc.perform(formLogin("/login").user("boris").password("secret2"))
                .andReturn().getRequest().getSession();
        mvc.perform(get("/dashboard").session(product)).andExpect(status().isOk());
        mvc.perform(get("/catalog").session(sales)).andExpect(status().isOk());
        mvc.perform(get("/dashboard").session(sales)).andExpect(status().isForbidden());
        mvc.perform(get("/feedback").session(sales)).andExpect(status().isForbidden());
    }

    @Test
    void existingHubUserKeepsHisRecordWhenSigningInThroughLdap() throws Exception {
        Long before = jdbc.queryForObject("select id from app_user where login = 'kuznetsov'", Long.class);
        mvc.perform(formLogin("/login").user("kuznetsov").password("secret4")).andExpect(authenticated().withUsername("kuznetsov"));
        assertEquals(1, jdbc.queryForObject("select count(*) from app_user where login = 'kuznetsov'", Integer.class));
        assertEquals(before, jdbc.queryForObject("select id from app_user where login = 'kuznetsov'", Long.class),
                "владелец SKU остается тем же пользователем, его SKU не теряются");
    }

    @Test
    void logoutEndsSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user("anna").password("secret1"))
                .andReturn().getRequest().getSession();
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/catalog").session(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void healthCheckIsOpenButShowsNoDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/env")).andExpect(status().is3xxRedirection()); // остальные адреса мониторинга закрыты входом
    }
}
