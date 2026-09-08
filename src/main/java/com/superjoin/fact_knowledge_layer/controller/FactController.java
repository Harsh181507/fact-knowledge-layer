package com.superjoin.fact_knowledge_layer.controller;

import com.superjoin.fact_knowledge_layer.dto.FactResponseDTO;
import com.superjoin.fact_knowledge_layer.model.Fact;
import com.superjoin.fact_knowledge_layer.repository.FactRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/facts")
public class FactController {

    private final FactRepository factRepository;

    public FactController(FactRepository factRepository) {
        this.factRepository = factRepository;
    }

    @GetMapping
    public List<FactResponseDTO> list(@RequestParam(required = false) Boolean needsReview) {
        List<Fact> facts = factRepository.findAll();
        return facts.stream()
                .filter(f -> needsReview == null || f.isNeedsReview() == needsReview)
                .map(FactResponseDTO::from)
                .toList();
    }

    @GetMapping("/{id}")
    public FactResponseDTO get(@PathVariable Long id) {
        Fact fact = factRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Fact " + id + " not found"));
        return FactResponseDTO.from(fact);
    }
}
