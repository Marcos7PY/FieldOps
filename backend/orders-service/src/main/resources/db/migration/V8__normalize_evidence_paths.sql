UPDATE work_order_evidence SET file_path = REPLACE(file_path, '/uploads/', '') WHERE file_path LIKE '/uploads/%';
