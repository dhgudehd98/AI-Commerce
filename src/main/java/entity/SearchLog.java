package entity;

import enums.search.SearchType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false)
    private String keyword;

    @Column(nullable = false)
    private String queryText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SearchType searchType;

    private String normalizedQuery;

    @Column(length = 2000)
    private String filtersJson;

    @Column(length = 2000)
    private String resultProductIdsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clicked_product_id")
    private Product clickedProduct;

    private String sessionId;

    private String modelName;

    private String promptVersion;

    private Integer latencyMs;

    private Integer resultCount;

    @Column(nullable = false)
    private LocalDateTime searchedAt;
}
