INSERT INTO d_user (
    first_name,
    last_name,
    email,
    id_card,
    phone_number,
    password_hash,
    must_change_password,
    status
) VALUES (
    'admin',
    'generic',
    'admin@generic.com',
    'noID',
    'noPhone',
    '$2a$12$US00g/uMhoSBm.HiuieBjeMtoN69SN.GE25fCpldebzkryUyopws6',
    FALSE,
    'ACTIVA'
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_role (
    user_id,
    role
) VALUES (
    1,
    'ADMIN'
)