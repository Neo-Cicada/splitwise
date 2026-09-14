# Splitwise

Expense-splitting app. Spring Boot 3, Java 21, Postgres, Thymeleaf.

## Setup

```bash
# 1. Start Postgres (database splitwise, user/password splitwise, port 5432)
docker compose up -d

# 2. Run the application
./mvnw spring-boot:run
```

Then open http://localhost:8080.

To boot with a populated demo group, run with the `dev` profile:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The dev seeder logs the sign-in credentials and the seeded group's id at startup.

## Notes

### Money

Every amount is `BigDecimal` mapped to `NUMERIC(12,2)`. No `double` or `float`
anywhere. Equal splits round each share down and hand the leftover cents to the
first participants, so 100.00 across 3 is 33.34 / 33.33 / 33.33 rather than
three shares that quietly lose a cent.

### Balances

Balances are never stored. They are derived on every request from `expenses`,
`expense_shares` and `settlements` by a single native query with four CTEs. The
service asserts that every group's balances sum to zero before returning them,
so a derivation bug surfaces immediately instead of showing wrong money.

### Debt simplification is greedy, not minimal

The suggested payments come from a greedy algorithm: repeatedly match the
largest debtor against the largest creditor and transfer the smaller of the two
amounts. Each pass zeroes at least one person, so a group of n members settles
in at most n−1 payments.

This is **not** guaranteed to be the minimum possible number of transactions.
Finding that true minimum is NP-hard — it contains subset-sum, since any subset
of members whose balances cancel among themselves could settle internally, and
identifying every such subset is the hard part. The n−1 bound is the practical
answer, it is what real expense-splitting apps ship, and it is what this does.

Suggestions are only ever suggestions. Clicking one pre-fills the settlement
form; nothing is recorded until the form is submitted, because a settlement
means money actually moved.

## Testing

```bash
./mvnw test
```

Split arithmetic and debt simplification are plain JUnit with no Spring context.
Balance and settlement behaviour runs against a real Postgres 16 container via
Testcontainers, not H2 — H2 differs on `NUMERIC` precision and CTE handling, so
passing there would prove nothing about production.
