package ru.eltex.producthub.feedback.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Комментарий к странице или элементу интерфейса. Ответы - такие же записи с parent_id. */
@Entity
@Table(name = "feedback_comment")
public class FeedbackComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "page_path", nullable = false)
    private String pagePath;

    @Column(name = "page_query")
    private String pageQuery;

    @Column(name = "page_title")
    private String pageTitle;

    @Column(name = "route_pattern")
    private String routePattern;

    @Column(name = "view_name")
    private String viewName;

    @Column(name = "selector")
    private String selector;

    @Column(name = "element_tag")
    private String elementTag;

    @Column(name = "element_text")
    private String elementText;

    @Column(name = "element_html")
    private String elementHtml;

    @Column(name = "section_title")
    private String sectionTitle;

    @Column(name = "context_text")
    private String contextText;

    @Column(name = "viewport")
    private String viewport;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private CommentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CommentStatus status = CommentStatus.NEW;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "author_login", nullable = false)
    private String authorLogin;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected FeedbackComment() {
    }

    public FeedbackComment(String authorLogin, String authorName, CommentKind kind, String body, String pagePath) {
        this.authorLogin = authorLogin;
        this.authorName = authorName;
        this.kind = kind;
        this.body = body;
        this.pagePath = pagePath;
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getPagePath() {
        return pagePath;
    }

    public String getPageQuery() {
        return pageQuery;
    }

    public void setPageQuery(String pageQuery) {
        this.pageQuery = pageQuery;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getRoutePattern() {
        return routePattern;
    }

    public void setRoutePattern(String routePattern) {
        this.routePattern = routePattern;
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getElementTag() {
        return elementTag;
    }

    public void setElementTag(String elementTag) {
        this.elementTag = elementTag;
    }

    public String getElementText() {
        return elementText;
    }

    public void setElementText(String elementText) {
        this.elementText = elementText;
    }

    public String getElementHtml() {
        return elementHtml;
    }

    public void setElementHtml(String elementHtml) {
        this.elementHtml = elementHtml;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public void setSectionTitle(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }

    public String getContextText() {
        return contextText;
    }

    public void setContextText(String contextText) {
        this.contextText = contextText;
    }

    public String getViewport() {
        return viewport;
    }

    public void setViewport(String viewport) {
        this.viewport = viewport;
    }

    public CommentKind getKind() {
        return kind;
    }

    public CommentStatus getStatus() {
        return status;
    }

    public void setStatus(CommentStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getBody() {
        return body;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    public String getAuthorLogin() {
        return authorLogin;
    }

    public String getAuthorName() {
        return authorName;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
