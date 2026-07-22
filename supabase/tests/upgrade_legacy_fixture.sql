\set ON_ERROR_STOP on

-- Representative rows accepted by 0001..0005 but rejected by the staged integrity
-- contracts in 0006. The profile itself remains valid because 0007 intentionally
-- recalculates every user's derived gamification columns during its expand phase.
insert into auth.users (id, email, raw_user_meta_data)
values (
    '00000000-0000-0000-0000-000000000090',
    'legacy-upgrade@example.test',
    '{"full_name":"Legacy Upgrade"}'::jsonb
);

insert into public.users (
    id, auth_id, email, display_name, monthly_budget, currency,
    notification_enabled, notification_hour, notification_types
) values (
    '10000000-0000-0000-0000-000000000090',
    '00000000-0000-0000-0000-000000000090',
    'legacy-upgrade@example.test',
    'Legacy Upgrade',
    500,
    'PEN',
    true,
    20,
    array['progress', 'reminder']::text[]
);

insert into public.categories (
    id, user_id, name, icon, color, is_default, display_order
) values (
    '40000000-0000-0000-0000-000000000090',
    '10000000-0000-0000-0000-000000000090',
    ' ',
    'tag',
    '#123ABC',
    false,
    90
);

insert into public.expenses (
    id, user_id, category_id, amount, description, expense_date,
    source, recipient, image_url
) values (
    '50000000-0000-0000-0000-000000000090',
    '10000000-0000-0000-0000-000000000090',
    '40000000-0000-0000-0000-000000000090',
    -5,
    repeat('d', 501),
    date '1999-12-31',
    'manual',
    repeat('r', 161),
    'javascript:legacy'
);

insert into public.goals (
    id, user_id, name, target_amount, current_amount, deadline,
    status, completed_at
) values (
    '60000000-0000-0000-0000-000000000090',
    '10000000-0000-0000-0000-000000000090',
    'Legacy goal',
    -100,
    -5,
    date '2030-01-01',
    'active',
    now()
);

insert into public.goal_deposits (
    id, goal_id, user_id, amount
) values (
    '70000000-0000-0000-0000-000000000090',
    '60000000-0000-0000-0000-000000000090',
    '10000000-0000-0000-0000-000000000090',
    -2
);

insert into public.badges (
    id, user_id, badge_type, name, description, criteria
) values (
    '80000000-0000-0000-0000-000000000090',
    '10000000-0000-0000-0000-000000000090',
    'legacy_unknown',
    ' ',
    repeat('b', 501),
    repeat('c', 501)
);

insert into public.budget_extras (
    id, user_id, month, amount, note
) values (
    '90000000-0000-0000-0000-000000000090',
    '10000000-0000-0000-0000-000000000090',
    date '2020-01-01',
    25,
    repeat('n', 241)
);
