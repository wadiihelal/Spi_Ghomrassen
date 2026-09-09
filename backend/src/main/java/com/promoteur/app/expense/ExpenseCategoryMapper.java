package com.promoteur.app.expense;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExpenseCategoryMapper {

    ExpenseCategoryResponse toResponse(ExpenseCategory category);
}
