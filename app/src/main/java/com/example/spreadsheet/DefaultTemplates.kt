package com.example.spreadsheet

import com.example.model.SpreadsheetTemplate

object DefaultTemplates {
    val BusinessExpenses = SpreadsheetTemplate(
        id = "business_expenses",
        title = "Business Expenses",
        description = "Standard corporate expense reporting with tax and payment tracking",
        iconName = "ReceiptLong",
        columns = listOf(
            "Date",
            "Merchant",
            "Category",
            "Description",
            "Subtotal",
            "Tax",
            "Tip",
            "Total Amount",
            "Payment Method",
            "Notes"
        ),
        initialRows = listOf(
            mapOf(
                "Date" to "2026-10-08",
                "Merchant" to "Office Supply Co",
                "Category" to "Office Supplies",
                "Description" to "Printer ink and paper",
                "Subtotal" to "64.50",
                "Tax" to "5.16",
                "Tip" to "0.00",
                "Total Amount" to "69.66",
                "Payment Method" to "Corporate Visa",
                "Notes" to "Q4 stationery"
            )
        )
    )

    val TravelMeals = SpreadsheetTemplate(
        id = "travel_meals",
        title = "Travel & Per-Diem",
        description = "Meals, transportation, lodging, and client entertainment logs",
        iconName = "FlightTakeoff",
        columns = listOf(
            "Date",
            "City / Location",
            "Category",
            "Merchant",
            "Total Amount",
            "Reimbursable",
            "Payment Method",
            "Purpose"
        ),
        initialRows = listOf(
            mapOf(
                "Date" to "2026-10-09",
                "City / Location" to "Chicago, IL",
                "Category" to "Meals",
                "Merchant" to "Bistro Central",
                "Total Amount" to "42.80",
                "Reimbursable" to "Yes",
                "Payment Method" to "Amex *4021",
                "Purpose" to "Client Lunch Meeting"
            )
        )
    )

    val TaxDeductions = SpreadsheetTemplate(
        id = "tax_deductions",
        title = "Tax Deductions",
        description = "Detailed records for year-end write-offs and accountant audit trails",
        iconName = "AccountBalance",
        columns = listOf(
            "Date",
            "Payee",
            "Expense Category",
            "Business Purpose",
            "Amount",
            "Tax Paid",
            "Payment Ref"
        ),
        initialRows = emptyList()
    )

    val ContractorInvoices = SpreadsheetTemplate(
        id = "contractor_invoices",
        title = "Contractor & Freelance",
        description = "Track reimbursable project expenses and billable materials",
        iconName = "WorkOutline",
        columns = listOf(
            "Date",
            "Client / Project",
            "Vendor",
            "Item / Service",
            "Cost",
            "Tax Deductible",
            "Payment Method"
        ),
        initialRows = emptyList()
    )

    val allTemplates = listOf(BusinessExpenses, TravelMeals, TaxDeductions, ContractorInvoices)
}
