-- KHONG CHAY BAN XOA TOAN BO TAI KHOAN CU.
-- Yeu cau moi cua nguoi dung: giu thong tin tai khoan va quyen dang nhap.
-- Mo scripts/sql/reset_data_keep_accounts.sql va chay toan bo file do.
DO $stop$
BEGIN
    RAISE EXCEPTION 'File nay da dung su dung de bao ve tai khoan. Hay dung reset_data_keep_accounts.sql.';
END;
$stop$;
