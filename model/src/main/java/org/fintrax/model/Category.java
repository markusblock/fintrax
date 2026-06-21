package org.fintrax.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    private Long id;
    private String name;
    private Long parentId;
    private String color;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
