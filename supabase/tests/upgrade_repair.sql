\set ON_ERROR_STOP on

-- This fixture models an operator-reviewed repair: preserve every historical entity and
-- replace only values that violate the newly documented business contracts.
update public.categories
set name = 'Legacy repaired'
where id = '40000000-0000-0000-0000-000000000090';

update public.expenses
set amount = 5,
    description = 'Repaired legacy description',
    expense_date = date '2000-01-01',
    recipient = 'Repaired recipient',
    image_url = null,
    client_request_id = '31000000-0000-0000-0000-000000000090'
where id = '50000000-0000-0000-0000-000000000090';

update public.goals
set target_amount = 100,
    current_amount = 10,
    status = 'active',
    completed_at = null,
    client_request_id = '32000000-0000-0000-0000-000000000090'
where id = '60000000-0000-0000-0000-000000000090';

update public.goal_deposits
set amount = 10,
    request_id = '30000000-0000-0000-0000-000000000090',
    balance_after = 10,
    completed_goal = false,
    deposit_count_after = 1
where id = '70000000-0000-0000-0000-000000000090';

update public.budget_extras
set note = 'Repaired legacy extra',
    client_request_id = '33000000-0000-0000-0000-000000000090'
where id = '90000000-0000-0000-0000-000000000090';

-- Repair this last: validate_badge_unlock now sees the repaired positive expense and can
-- prove the first_expense criterion before accepting the legacy row's new badge type.
update public.badges
set badge_type = 'first_expense',
    name = 'Primer gasto',
    description = 'Repaired legacy badge',
    criteria = 'At least one expense exists'
where id = '80000000-0000-0000-0000-000000000090';
