package com.internlink.core.application.placement.dossier;

import java.util.List;
import java.util.Optional;

/** Bộ hồ sơ của một lần thực tập tại thời điểm đọc. */
public record DossierSnapshot(List<DossierEntry> entries) {

    public List<DossierEntry> required() {
        return entries.stream().filter(e -> e.item().mandatory()).toList();
    }

    public int requiredCount() {
        return required().size();
    }

    public int submittedCount() {
        return (int) required().stream().filter(DossierEntry::isSubmitted).count();
    }

    public int approvedCount() {
        return (int) required().stream().filter(DossierEntry::isApproved).count();
    }

    public boolean isComplete() {
        return approvedCount() == requiredCount();
    }

    public List<String> problems() {
        return required().stream().filter(e -> !e.isApproved()).map(DossierEntry::problemLabel).toList();
    }

    public Optional<DossierEntry> entry(DossierItem item) {
        return entries.stream().filter(e -> e.item() == item).findFirst();
    }
}
