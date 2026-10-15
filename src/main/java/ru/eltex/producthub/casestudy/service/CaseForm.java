package ru.eltex.producthub.casestudy.service;

import java.util.ArrayList;
import java.util.List;

public class CaseForm {

    private String title;
    private String country;
    private String city;
    private String customerName;
    private boolean customerVisible;
    private String summary;
    private String challenge;
    private String solution;
    private String results;
    private String deploymentSize;
    private String year;
    private Long ownerId;
    private boolean publicationAllowed;
    private String skus;
    private List<Long> tagIds = new ArrayList<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public boolean isCustomerVisible() {
        return customerVisible;
    }

    public void setCustomerVisible(boolean customerVisible) {
        this.customerVisible = customerVisible;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    public String getResults() {
        return results;
    }

    public void setResults(String results) {
        this.results = results;
    }

    public String getDeploymentSize() {
        return deploymentSize;
    }

    public void setDeploymentSize(String deploymentSize) {
        this.deploymentSize = deploymentSize;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isPublicationAllowed() {
        return publicationAllowed;
    }

    public void setPublicationAllowed(boolean publicationAllowed) {
        this.publicationAllowed = publicationAllowed;
    }

    public String getSkus() {
        return skus;
    }

    public void setSkus(String skus) {
        this.skus = skus;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds == null ? new ArrayList<>() : tagIds;
    }
}
