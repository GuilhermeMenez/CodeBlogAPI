package blog.code.codeblog.mapper;

import blog.code.codeblog.command.search.SearchPostsCommand;
import blog.code.codeblog.command.search.SearchUsersCommand;
import blog.code.codeblog.command.search.SuggestCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchMapperTest {

    @Test
    @DisplayName("toSearchPostsCommand trims the query")
    void toSearchPostsCommandTrimsQuery() {
        SearchPostsCommand command = SearchMapper.toSearchPostsCommand("  java  ", 0, 10);

        assertEquals("java", command.getQuery());
        assertEquals(0, command.getPage());
        assertEquals(10, command.getSize());
    }

    @ParameterizedTest(name = "page {0} → {1}, size {2} → {3}")
    @CsvSource({
            "-1, 0, 0, 1",
            "-5, 0, -3, 1",
            "2, 2, 51, 50",
            "0, 0, 1000, 50",
            "3, 3, 25, 25"
    })
    @DisplayName("toSearchPostsCommand turns a negative page into 0 and clamps size between 1 and 50")
    void toSearchPostsCommandClampsPageAndSize(int page, int expectedPage, int size, int expectedSize) {
        SearchPostsCommand command = SearchMapper.toSearchPostsCommand("java", page, size);

        assertEquals(expectedPage, command.getPage());
        assertEquals(expectedSize, command.getSize());
    }

    @ParameterizedTest(name = "page {0} → {1}, size {2} → {3}")
    @CsvSource({
            "-1, 0, 0, 1",
            "1, 1, 99, 50",
            "0, 0, 10, 10"
    })
    @DisplayName("toSearchUsersCommand trims the query, turns a negative page into 0 and clamps size between 1 and 50")
    void toSearchUsersCommandClampsPageAndSize(int page, int expectedPage, int size, int expectedSize) {
        SearchUsersCommand command = SearchMapper.toSearchUsersCommand(" gui ", page, size);

        assertEquals("gui", command.getQuery());
        assertEquals(expectedPage, command.getPage());
        assertEquals(expectedSize, command.getSize());
    }

    @ParameterizedTest(name = "limit {0} → {1}")
    @CsvSource({
            "0, 1",
            "-2, 1",
            "5, 5",
            "10, 10",
            "11, 10",
            "100, 10"
    })
    @DisplayName("toSuggestCommand trims the query and clamps limit between 1 and 10")
    void toSuggestCommandClampsLimit(int limit, int expectedLimit) {
        SuggestCommand command = SearchMapper.toSuggestCommand(" ja ", limit);

        assertEquals("ja", command.getQuery());
        assertEquals(expectedLimit, command.getLimit());
    }
}
