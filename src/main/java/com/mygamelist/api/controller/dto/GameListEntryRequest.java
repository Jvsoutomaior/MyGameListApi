package com.mygamelist.api.controller.dto;

import com.mygamelist.api.entity.GameStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GameListEntryRequest(
    @NotNull GameStatus status, @Min(1) @Max(10) Integer score, @Size(max = 5000) String comment) {}
