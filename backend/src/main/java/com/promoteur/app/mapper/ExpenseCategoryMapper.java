package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.ExpenseCategoryResponse;
import com.promoteur.app.entity.ExpenseCategory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExpenseCategoryMapper {

    ExpenseCategoryResponse toResponse(ExpenseCategory category);
}
