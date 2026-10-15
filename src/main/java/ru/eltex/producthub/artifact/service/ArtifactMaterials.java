package ru.eltex.producthub.artifact.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.sku.service.CompletenessProperties;
import ru.eltex.producthub.sku.service.MaterialsSummary;
import ru.eltex.producthub.sku.service.SkuMaterials;

/** Сведения о материалах SKU для расчета заполненности (SPEC.md 5.4). */
@Component
public class ArtifactMaterials implements SkuMaterials {

    private final ArtifactRepository artifacts;
    private final CompletenessProperties props;

    public ArtifactMaterials(ArtifactRepository artifacts, CompletenessProperties props) {
        this.artifacts = artifacts;
        this.props = props;
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialsSummary summary(Long skuId) {
        boolean ru = false;
        boolean en = false;
        boolean photo = false;
        for (Object[] row : artifacts.findActualLanguagesAndTypes(skuId)) {
            Language language = (Language) row[0];
            String type = (String) row[1];
            if (type.equals(props.getDatasheetType())) {
                ru |= language == Language.RU || language == Language.RU_EN;
                en |= language == Language.EN || language == Language.RU_EN;
            } else if (type.equals(props.getPhotoType())) {
                photo = true;
            }
        }
        return new MaterialsSummary(ru, en, photo);
    }
}
