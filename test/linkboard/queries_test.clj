(ns linkboard.queries-test
  (:require [clojure.test :refer :all]
            [linkboard.queries :refer [preprocess-search-query]]))

(deftest test-preprocess-search-query
  (testing "Basic token processing with wildcards"
    (is (= "hello* world*"
           (preprocess-search-query "hello world")))
    (is (= "test*"
           (preprocess-search-query "test"))))

  (testing "Special character quoting"
    (is (= "\"openai.com\"* cool* stuff*"
           (preprocess-search-query "openai.com cool stuff")))
    (is (= "\"user@domain.com\"*"
           (preprocess-search-query "user@domain.com")))
    (is (= "\"api/v1/users\"*"
           (preprocess-search-query "api/v1/users")))
    (is (= "\"some-file.txt\"*"
           (preprocess-search-query "some-file.txt")))
    (is (= "\"config:value\"*"
           (preprocess-search-query "config:value"))))

  (testing "FTS5 operators preserved without wildcards"
    (is (= "github* and code*"
           (preprocess-search-query "github AND code")))
    (is (= "react* or vue*"
           (preprocess-search-query "react OR vue")))
    (is (= "not spam*"
           (preprocess-search-query "NOT spam")))
    (is (= "near search*"
           (preprocess-search-query "NEAR search"))))

  (testing "Case normalization"
    (is (= "github* and code*"
           (preprocess-search-query "GitHub AND Code")))
    (is (= "\"openai.com\"*"
           (preprocess-search-query "OpenAI.COM"))))

  (testing "Punctuation stripping"
    (is (= "hello* world*"
           (preprocess-search-query "hello() world{}")))
    (is (= "test* data*"
           (preprocess-search-query "test[,] data;"))))

  (testing "Whitespace handling"
    (is (= "multiple* spaces*"
           (preprocess-search-query "  multiple    spaces  ")))
    (is (= "tab* separated*"
           (preprocess-search-query "tab\tseparated"))))

  (testing "Empty and invalid inputs"
    (is (nil? (preprocess-search-query "")))
    (is (nil? (preprocess-search-query "   ")))
    (is (nil? (preprocess-search-query "\t\n")))
    (is (nil? (preprocess-search-query nil))))

  (testing "Mixed operators and special chars"
    (is (= "\"api.github.com\"* and \"docs/readme.md\"*"
           (preprocess-search-query "api.github.com AND docs/readme.md")))
    (is (= "\"user@example.com\"* or \"admin:password\"*"
           (preprocess-search-query "user@example.com OR admin:password"))))

  (testing "Edge cases with only punctuation"
    (is (nil? (preprocess-search-query "(){}[]")))
    (is (= "\"...\"*"
           (preprocess-search-query "..."))))

  (testing "Complex real-world examples"
    (is (= "\"stackoverflow.com\"* javascript* tutorial*"
           (preprocess-search-query "stackoverflow.com javascript tutorial")))
    (is (= "\"react-router\"* and typescript*"
           (preprocess-search-query "react-router AND typescript")))
    (is (= "\"localhost:3000\"* api* endpoint*"
           (preprocess-search-query "localhost:3000 API endpoint")))))

(deftest test-preprocess-search-query-integration
  (testing "Integration with common search patterns"
    ; Domain searches
    (is (= "\"github.com\"*"
           (preprocess-search-query "github.com")))

    ; Technology searches
    (is (= "clojure* and tutorial*"
           (preprocess-search-query "clojure AND tutorial")))

    ; File path searches  
    (is (= "\"src/main.js\"*"
           (preprocess-search-query "src/main.js")))

    ; Email searches
    (is (= "\"contact@company.com\"*"
           (preprocess-search-query "contact@company.com")))

    ; Version searches
    (is (= "\"v1.2.3\"*"
           (preprocess-search-query "v1.2.3")))))