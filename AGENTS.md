# AGENT CODING RULES FOR OMNIBID BACKEND

Agent MUST strictly comply with the following architectural rules on EVERY code generation:

1. ARCHITECTURE & DEPENDENCIES:
   - Follow strict Monolith 3-layer architecture: Controller -> Service -> Repository / Entity.
   - Controller MUST NEVER inject or call Repository or Entity directly. Only inject Service.
   - Controller MUST NEVER return Entity directly. Always wrap data into DTOs and ApiResponse<T>.

2. DATA INTEGRITY & API CONTRACT:
   - Unified API Response format: Every endpoint must return ApiResponse<T>.
   - Input validation: All Request DTOs must use Jakarta Validation annotations (@NotNull, @NotBlank, @Min, etc.) and Controllers must declare @Valid.
   - Status updates over hard delete: NEVER perform hard delete on operational data (users, products, auctions, orders). Use status enums (BANNED, HIDDEN, REMOVED).

3. SECURITY & TRANSACTION:
   - Never bypass BCryptPasswordEncoder for passwords.
   - Never save raw passwords into the database.
   - Concurrency for auctions: Bidding transactions must use `findByIdForUpdate` (Pessimistic Lock).

4. VERIFICATION:
   - Before finishing any task, the Agent MUST run `.\mvnw.cmd test-compile` to ensure 0 compilation errors.
