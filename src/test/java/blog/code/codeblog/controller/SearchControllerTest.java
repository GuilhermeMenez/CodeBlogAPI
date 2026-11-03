package blog.code.codeblog.controller;

import blog.code.codeblog.config.handlers.GlobalExceptionHandler;
import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.dto.search.SearchSuggestionsDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.facade.SearchFacade;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchControllerTest {

    private SearchFacade searchFacade;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        searchFacade = mock(SearchFacade.class);
        SearchController controller = new SearchController();
        controller.searchFacade = searchFacade;

        // na aplicação o @Validated cria este proxy; aqui ele é montado à mão para o teste não subir o contexto
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProxyFactory proxyFactory = new ProxyFactory(controller);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new MethodValidationInterceptor((Validator) validator));

        mockMvc = MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /search/posts returns 200 with a valid query")
    void searchPostsReturns200() throws Exception {
        when(searchFacade.searchPosts("java", 0, 10)).thenReturn(emptyPage());

        mockMvc.perform(get("/search/posts").param("q", "java"))
                .andExpect(status().isOk());

        verify(searchFacade).searchPosts("java", 0, 10);
    }

    @Test
    @DisplayName("GET /search/posts returns 400 when q is blank")
    void searchPostsBlankQueryReturns400() throws Exception {
        mockMvc.perform(get("/search/posts").param("q", "  "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchFacade);
    }

    @Test
    @DisplayName("GET /search/posts returns 400 when q is longer than 100 characters")
    void searchPostsTooLongQueryReturns400() throws Exception {
        mockMvc.perform(get("/search/posts").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchFacade);
    }

    @Test
    @DisplayName("GET /search/posts returns 400 when q is missing")
    void searchPostsMissingQueryReturns400() throws Exception {
        mockMvc.perform(get("/search/posts"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchFacade);
    }

    @Test
    @DisplayName("GET /search/users returns 200 with a valid query")
    void searchUsersReturns200() throws Exception {
        when(searchFacade.searchUsers("gui", 1, 5)).thenReturn(PageResponseDTO.<UserFollowDTO>builder().content(List.of()).build());

        mockMvc.perform(get("/search/users").param("q", "gui").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        verify(searchFacade).searchUsers("gui", 1, 5);
    }

    @Test
    @DisplayName("GET /search/users returns 400 when q is blank")
    void searchUsersBlankQueryReturns400() throws Exception {
        mockMvc.perform(get("/search/users").param("q", ""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchFacade);
    }

    @Test
    @DisplayName("GET /search/suggest returns 400 with a single character")
    void suggestSingleCharacterReturns400() throws Exception {
        mockMvc.perform(get("/search/suggest").param("q", "j"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchFacade);
    }

    @Test
    @DisplayName("GET /search/suggest returns 200 with two characters")
    void suggestTwoCharactersReturns200() throws Exception {
        when(searchFacade.suggest(anyString(), anyInt())).thenReturn(new SearchSuggestionsDTO(List.of(), List.of()));

        mockMvc.perform(get("/search/suggest").param("q", "ja"))
                .andExpect(status().isOk());

        verify(searchFacade).suggest("ja", 5);
    }

    private static PageResponseDTO<PostResponseDTO> emptyPage() {
        return PageResponseDTO.<PostResponseDTO>builder().content(List.of()).build();
    }
}
