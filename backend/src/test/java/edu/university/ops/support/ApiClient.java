package edu.university.ops.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Small helper for readable scenario tests: log in as a persona and call the JSON API. */
public final class ApiClient {

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final MockHttpSession session;

    private ApiClient(MockMvc mvc, ObjectMapper json, MockHttpSession session) {
        this.mvc = mvc;
        this.json = json;
        this.session = session;
    }

    public static ApiClient login(MockMvc mvc, ObjectMapper json, String username) throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"demo123\"}")).andReturn();
        if (r.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed for " + username);
        }
        return new ApiClient(mvc, json, (MockHttpSession) r.getRequest().getSession());
    }

    public Response get(String url) throws Exception {
        return perform(MockMvcRequestBuilders.get(url));
    }

    public Response post(String url, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.post(url).with(csrf()), body));
    }

    public Response put(String url, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.put(url).with(csrf()), body));
    }

    public Response perform(MockHttpServletRequestBuilder request) throws Exception {
        return new Response(mvc.perform(request.session(session)).andReturn(), json);
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return request;
    }

    public record Response(MvcResult result, ObjectMapper mapper) {
        public int status() {
            return result.getResponse().getStatus();
        }

        public JsonNode body() {
            try {
                String content = result.getResponse().getContentAsString();
                return content.isEmpty() ? mapper.nullNode() : mapper.readTree(content);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public String errorCode() {
            return body().path("code").asText(null);
        }

        public Response expect(int expected) {
            if (status() != expected) {
                throw new AssertionError("Expected HTTP " + expected + " but got " + status() + ": " + body());
            }
            return this;
        }
    }
}
