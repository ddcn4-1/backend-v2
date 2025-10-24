--  -- 기존 테이블 정리 (개발용)
DROP TABLE IF EXISTS system_metrics CASCADE;
DROP TABLE IF EXISTS refunds CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS booking_seats CASCADE;
DROP TABLE IF EXISTS bookings CASCADE;
DROP TABLE IF EXISTS seat_locks CASCADE;
DROP TABLE IF EXISTS schedule_seats CASCADE;
DROP TABLE IF EXISTS performance_schedules CASCADE;
DROP TABLE IF EXISTS performances CASCADE;
DROP TABLE IF EXISTS venues CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS queue_tokens CASCADE;

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
                                     user_id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) CHECK (role IN ('USER', 'ADMIN', 'DEVOPS', 'DEV')) DEFAULT 'USER',
    status VARCHAR(20) CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')) DEFAULT 'ACTIVE',
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 큐 토큰
CREATE TABLE queue_tokens (
                              token_id BIGSERIAL PRIMARY KEY,
                              token VARCHAR(64) NOT NULL UNIQUE,
                              user_id VARCHAR(255) NOT NULL,
                              performance_id BIGINT NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
                              position_in_queue INTEGER,
                              estimated_wait_time INTEGER,
                              issued_at TIMESTAMP NOT NULL,
                              expires_at TIMESTAMP NOT NULL,
                              activated_at TIMESTAMP,
                              used_at TIMESTAMP,
                              booking_expires_at TIMESTAMP,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 공연장 테이블
CREATE TABLE IF NOT EXISTS venues (
                                      venue_id BIGSERIAL PRIMARY KEY,
                                      venue_name VARCHAR(255) NOT NULL,
    address TEXT,
    description TEXT,
    total_capacity INTEGER NOT NULL DEFAULT 0,
    contact VARCHAR(100),
    seat_map_url TEXT,
    seat_map_json JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 공연 테이블
CREATE TABLE IF NOT EXISTS performances (
                                            performance_id BIGSERIAL PRIMARY KEY,
                                            venue_id BIGINT NOT NULL REFERENCES venues(venue_id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    theme VARCHAR(100),
    poster_url TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    running_time INTEGER DEFAULT 0,
    base_price DECIMAL(10,2) DEFAULT 0.00,
    status VARCHAR(20) CHECK (status IN ('UPCOMING', 'ONGOING', 'ENDED', 'CANCELLED')) DEFAULT 'UPCOMING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 공연 일정 테이블
CREATE TABLE IF NOT EXISTS performance_schedules (
                                                     schedule_id BIGSERIAL PRIMARY KEY,
                                                     performance_id BIGINT NOT NULL REFERENCES performances(performance_id),
    show_datetime TIMESTAMP NOT NULL,
    total_seats INTEGER DEFAULT 0,
    available_seats INTEGER DEFAULT 0,
    status VARCHAR(20) CHECK (status IN ('OPEN', 'CLOSED', 'SOLDOUT')) DEFAULT 'OPEN',
    booking_start_at TIMESTAMP,
    booking_end_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 회차별 좌석 테이블 (새로운 구조)
CREATE TABLE IF NOT EXISTS schedule_seats (
                                              seat_id BIGSERIAL PRIMARY KEY,
                                              schedule_id BIGINT NOT NULL REFERENCES performance_schedules(schedule_id),
    grade VARCHAR(10) NOT NULL,
    zone VARCHAR(50),
    row_label VARCHAR(10) NOT NULL,
    col_num VARCHAR(10) NOT NULL,
    price DECIMAL(10,2) DEFAULT 0.00,
    status VARCHAR(20) CHECK (status IN ('AVAILABLE', 'LOCKED', 'BOOKED')) DEFAULT 'AVAILABLE',
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(schedule_id, zone, row_label, col_num)
    );

-- 좌석 잠금 테이블
CREATE TABLE IF NOT EXISTS seat_locks (
                                          lock_id BIGSERIAL PRIMARY KEY,
                                          seat_id BIGINT NOT NULL REFERENCES schedule_seats(seat_id),
    user_id VARCHAR(255) REFERENCES users(user_id),
    session_id VARCHAR(255),
    locked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(20) CHECK (status IN ('ACTIVE', 'EXPIRED', 'RELEASED')) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 예매 테이블
CREATE TABLE IF NOT EXISTS bookings (
                                        booking_id BIGSERIAL PRIMARY KEY,
                                        booking_number VARCHAR(50) UNIQUE NOT NULL,
    user_id VARCHAR(255) NOT NULL REFERENCES users(user_id),
    schedule_id BIGINT NOT NULL REFERENCES performance_schedules(schedule_id),
    seat_count INTEGER NOT NULL DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) CHECK (status IN ('CONFIRMED', 'CANCELLED')) DEFAULT 'CONFIRMED',
    expires_at TIMESTAMP,
    booked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 예매 좌석 테이블
CREATE TABLE IF NOT EXISTS booking_seats (
                                             booking_seat_id BIGSERIAL PRIMARY KEY,
                                             booking_id BIGINT NOT NULL REFERENCES bookings(booking_id),
    seat_id BIGINT NOT NULL REFERENCES schedule_seats(seat_id),
    seat_price DECIMAL(10,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 결제 테이블
CREATE TABLE IF NOT EXISTS payments (
                                        payment_id BIGSERIAL PRIMARY KEY,
                                        booking_id BIGINT NOT NULL REFERENCES bookings(booking_id),
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(20) CHECK (payment_method IN ('CARD', 'BANK', 'KAKAO_PAY', 'NAVER_PAY', 'TOSS_PAY')),
    status VARCHAR(20) CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')) DEFAULT 'PENDING',
    pg_response TEXT,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 환불 테이블
CREATE TABLE IF NOT EXISTS refunds (
                                       refund_id BIGSERIAL PRIMARY KEY,
                                       payment_id BIGINT REFERENCES payments(payment_id),
    booking_id BIGINT NOT NULL REFERENCES bookings(booking_id),
    refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    reason TEXT,
    status VARCHAR(20) CHECK (status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'REJECTED')) DEFAULT 'REQUESTED',
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 시스템 메트릭 테이블
CREATE TABLE IF NOT EXISTS system_metrics (
                                              metric_id BIGSERIAL PRIMARY KEY,
                                              timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                              active_users INTEGER DEFAULT 0,
                                              queue_length INTEGER DEFAULT 0,
                                              cpu_usage DECIMAL(5,2) DEFAULT 0.00,
    memory_usage DECIMAL(5,2) DEFAULT 0.00,
    request_count INTEGER DEFAULT 0,
    avg_response_time DECIMAL(10,3) DEFAULT 0.000,
    server_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 인덱스 생성 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_performances_venue_id ON performances(venue_id);
CREATE INDEX IF NOT EXISTS idx_performances_dates ON performances(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_performance_schedules_performance_id ON performance_schedules(performance_id);
CREATE INDEX IF NOT EXISTS idx_performance_schedules_datetime ON performance_schedules(show_datetime);
CREATE INDEX IF NOT EXISTS idx_schedule_seats_schedule_id ON schedule_seats(schedule_id);
CREATE INDEX IF NOT EXISTS idx_schedule_seats_status ON schedule_seats(status);
CREATE INDEX IF NOT EXISTS idx_schedule_seats_grade ON schedule_seats(grade);
CREATE INDEX IF NOT EXISTS idx_schedule_seats_zone ON schedule_seats(zone);
CREATE INDEX IF NOT EXISTS idx_seat_locks_seat_id ON seat_locks(seat_id);
CREATE INDEX IF NOT EXISTS idx_seat_locks_user_id ON seat_locks(user_id);
CREATE INDEX IF NOT EXISTS idx_seat_locks_expires_at ON seat_locks(expires_at);
CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_schedule_id ON bookings(schedule_id);
CREATE INDEX IF NOT EXISTS idx_bookings_booking_number ON bookings(booking_number);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_booking_seats_booking_id ON booking_seats(booking_id);
CREATE INDEX IF NOT EXISTS idx_booking_seats_seat_id ON booking_seats(seat_id);
CREATE INDEX IF NOT EXISTS idx_payments_booking_id ON payments(booking_id);
CREATE INDEX IF NOT EXISTS idx_payments_transaction_id ON payments(transaction_id);
CREATE INDEX IF NOT EXISTS idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX IF NOT EXISTS idx_refunds_booking_id ON refunds(booking_id);
CREATE INDEX IF NOT EXISTS idx_system_metrics_timestamp ON system_metrics(timestamp);
-- JSONB seatmap index
CREATE INDEX IF NOT EXISTS idx_venues_seatmap ON venues USING gin (seat_map_json);

-- ============================================
-- USERS
-- ============================================
-- 관리자 계정
INSERT INTO users (user_id, email, username, name, phone, role, status) VALUES
    ('admin-001', 'admin@ticket.com', 'admin', 'Administrator', '010-0000-0000', 'ADMIN', 'ACTIVE')
    ON CONFLICT (username) DO NOTHING;

-- 일반 사용자 계정들
INSERT INTO users (user_id, email, username, name, phone, role, status) VALUES
                                                                            ('user-001', 'user@ticket.com', 'testuser', 'Test User', '010-1111-1111', 'USER', 'ACTIVE'),
                                                                            ('user-002', 'user2@ticket.com', 'user2', 'user2', '010-1111-1111', 'USER', 'ACTIVE'),
                                                                            ('user-003', 'john@ticket.com', 'john', 'John Doe', '010-2222-2222', 'USER', 'ACTIVE'),
                                                                            ('user-004', 'jane@ticket.com', 'jane', 'Jane Smith', '010-3333-3333', 'USER', 'ACTIVE'),
                                                                            ('user-005', 'mike@ticket.com', 'mike', 'Mike Johnson', '010-4444-4444', 'USER', 'ACTIVE'),
                                                                            ('user-006', 'sarah@ticket.com', 'sarah', 'Sarah Wilson', '010-5555-5555', 'USER', 'ACTIVE')
    ON CONFLICT (username) DO NOTHING;

-- 개발자 계정
INSERT INTO users (user_id, email, username, name, phone, role, status) VALUES
    ('dev-001', 'dev@ticket.com', 'developer', 'Developer', '010-6666-6666', 'DEV', 'ACTIVE')
    ON CONFLICT (username) DO NOTHING;

-- DevOps 계정
INSERT INTO users (user_id, email, username, name, phone, role, status) VALUES
    ('ops-001', 'ops@ticket.com', 'devops', 'DevOps Engineer', '010-7777-7777', 'DEVOPS', 'ACTIVE')
    ON CONFLICT (username) DO NOTHING;


-- ============================================
-- VENUES
-- ============================================
INSERT INTO venues (venue_id, venue_name, address, description, total_capacity, contact, created_at) VALUES
                                                                                                         (1, 'Grand Opera House', '서울특별시 중구 세종대로 175', '서울의 대표적인 오페라 하우스로 클래식한 건축미와 최첨단 음향시설을 자랑합니다.', 1200, '02-399-1000', '2025-01-01 00:00:00'),
                                                                                                         (2, 'National Theater',  '서울특별시 중구 장충단로 59', '국립극장으로 전통과 현대가 조화된 다양한 공연을 선보입니다.', 1500, '02-2280-4114', '2025-01-01 00:00:00'),
                                                                                                         (3, 'Arena Stadium',     '서울특별시 송파구 올림픽로 424', '대규모 콘서트와 스포츠 이벤트를 위한 종합 경기장입니다.', 2000, '02-2240-8800', '2025-01-01 00:00:00'),
                                                                                                         (4, 'Symphony Hall',     '서울특별시 서초구 서초동 1330-15', '클래식 음악 전용 홀로 최고의 음향을 자랑합니다.', 1200, '02-525-1300', '2025-01-01 00:00:00'),
                                                                                                         (5, 'Art Center Seoul',  '서울특별시 강남구 테헤란로 518', '현대적인 복합 문화공간으로 다양한 장르의 공연이 가능합니다.', 1800, '02-6000-5000', '2025-01-01 00:00:00'),
                                                                                                         (6, 'Blue Square',       '서울특별시 용산구 이태원로 294', '뮤지컬 전용 극장으로 최신 시설을 갖추고 있습니다.', 1766, '1588-5212', '2025-01-01 00:00:00'),
                                                                                                         (7, 'LG Arts Center',    '서울특별시 강남구 역삼로 136', 'LG가 운영하는 프리미엄 공연장입니다.', 1354, '02-2005-0114', '2025-01-01 00:00:00'),
                                                                                                         (8, 'Intimate Hall',     '서울특별시 마포구 홍대입구로 94', '소규모 공연을 위한 아늑한 실내악 전용 홀입니다.', 100, '02-322-1234', '2025-01-01 00:00:00'),
                                                                                                         (9, 'Mini Theater',      '서울특별시 종로구 대학로 123', '10석 규모의 초소형 실험극장으로 혁신적인 공연을 선보입니다.', 10, '02-747-5678', '2025-01-01 00:00:00')
    ON CONFLICT (venue_id) DO NOTHING;

-- 좌석맵 URL/JSON 저장 (venue 1~3)
UPDATE venues SET
                  seat_map_url = '/seatmaps/venue_1.json',
                  seat_map_json = '{
                    "version": 1,
                    "meta": {"totalSeats": 1200, "seatCodeFormat": "{row}-{number}"},
                    "pricing": {"VIP": 120000, "R": 95000, "S": 80000, "A": 60000},
                    "sections": [
                      {"name":"Orchestra","zone":"A","grade":"VIP","rows":10,"cols":20,"rowLabelFrom":"A","seatStart":1},
                      {"name":"Front","zone":"B","grade":"R","rows":20,"cols":20,"rowLabelFrom":"K","seatStart":1},
                      {"name":"Middle","zone":"C","grade":"S","rows":15,"cols":20,"rowLabelFrom":"AE","seatStart":1},
                      {"name":"Balcony","zone":"D","grade":"A","rows":15,"cols":20,"rowLabelFrom":"AT","seatStart":1}
                    ]
                  }'::jsonb
WHERE venue_id = 1;

UPDATE venues SET
                  seat_map_url = '/seatmaps/venue_2.json',
                  seat_map_json = '{
                    "version": 1,
                    "meta": {"totalSeats": 1500, "seatCodeFormat": "{row}-{number}"},
                    "pricing": {"VIP": 130000, "R": 100000, "S": 85000, "A": 65000},
                    "sections": [
                      {"name":"Orchestra","zone":"A","grade":"VIP","rows":10,"cols":20,"rowLabelFrom":"A","seatStart":1},
                      {"name":"Front","zone":"B","grade":"R","rows":20,"cols":25,"rowLabelFrom":"K","seatStart":1},
                      {"name":"Middle","zone":"C","grade":"S","rows":15,"cols":25,"rowLabelFrom":"AE","seatStart":1},
                      {"name":"Balcony","zone":"D","grade":"A","rows":17,"cols":25,"rowLabelFrom":"AT","seatStart":1}
                    ]
                  }'::jsonb
WHERE venue_id = 2;

UPDATE venues SET
                  seat_map_url = '/seatmaps/venue_3.json',
                  seat_map_json = '{
                    "version": 1,
                    "meta": {"totalSeats": 2000, "seatCodeFormat": "{row}-{number}"},
                    "pricing": {"VIP": 150000, "R": 120000, "S": 90000, "A": 70000},
                    "sections": [
                      {"name":"Orchestra","zone":"A","grade":"VIP","rows":10,"cols":25,"rowLabelFrom":"A","seatStart":1},
                      {"name":"Front","zone":"B","grade":"R","rows":20,"cols":30,"rowLabelFrom":"K","seatStart":1},
                      {"name":"Middle","zone":"C","grade":"S","rows":20,"cols":25,"rowLabelFrom":"AE","seatStart":1},
                      {"name":"Balcony","zone":"D","grade":"A","rows":26,"cols":25,"rowLabelFrom":"Y","seatStart":1}
                    ]
                  }'::jsonb
WHERE venue_id = 3;

-- 100석 규모 공연장 (venue_id = 8)
UPDATE venues SET
                  seat_map_url = '/seatmaps/venue_8.json',
                  seat_map_json = '{
                    "version": 1,
                    "meta": {"totalSeats": 100, "seatCodeFormat": "{row}-{number}"},
                    "pricing": {"VIP": 80000, "R": 60000, "S": 45000, "A": 30000},
                    "sections": [
                      {"name":"VIP Section","zone":"A","grade":"VIP","rows":2,"cols":10,"rowLabelFrom":"A","seatStart":1},
                      {"name":"Premium","zone":"B","grade":"R","rows":3,"cols":10,"rowLabelFrom":"C","seatStart":1},
                      {"name":"Standard","zone":"C","grade":"S","rows":3,"cols":10,"rowLabelFrom":"F","seatStart":1},
                      {"name":"General","zone":"D","grade":"A","rows":2,"cols":10,"rowLabelFrom":"I","seatStart":1}
                    ]
                  }'::jsonb
WHERE venue_id = 8;

-- 10석 규모 공연장 (venue_id = 9)
UPDATE venues SET
                  seat_map_url = '/seatmaps/venue_9.json',
                  seat_map_json = '{
                    "version": 1,
                    "meta": {"totalSeats": 10, "seatCodeFormat": "{row}-{number}"},
                    "pricing": {"Premium": 50000, "Standard": 35000},
                    "sections": [
                      {"name":"Front Row","zone":"A","grade":"Premium","rows":1,"cols":5,"rowLabelFrom":"A","seatStart":1},
                      {"name":"Back Row","zone":"B","grade":"Standard","rows":1,"cols":5,"rowLabelFrom":"B","seatStart":1}
                    ]
                  }'::jsonb
WHERE venue_id = 9;

-- ============================================
-- PERFORMANCES (SCHEDULED→UPCOMING, COMPLETED→ENDED)
-- ============================================
INSERT INTO performances (performance_id, venue_id, title, description, theme, poster_url,
                          start_date, end_date, running_time, base_price, status, created_at) VALUES
                                                                                                  (1, 1, 'The Phantom of the Opera',
                                                                                                   'A breathtaking musical that tells the story of a mysterious figure haunting the Paris Opera House.',
                                                                                                   'Musical', 'performances/posters/20250924/00e8b72f.jpg',
                                                                                                   '2025-10-15', '2025-10-15', 180, 75000, 'UPCOMING', NOW()),
                                                                                                  (2, 2, 'Swan Lake Ballet',
                                                                                                   'A classical ballet performance featuring graceful dancers and Tchaikovsky''s timeless music.',
                                                                                                   'Ballet', 'performances/posters/20250924/03760446.jpg',
                                                                                                   '2025-09-07', '2025-09-07', 150, 65000, 'UPCOMING', NOW()),
                                                                                                  (3, 3, 'Rock Concert Live',
                                                                                                   'An electrifying rock concert featuring top bands and explosive performances.',
                                                                                                   'Concert', 'performances/posters/20250924/075a2b00.png',
                                                                                                   '2025-12-21', '2025-12-21', 240, 80000, 'UPCOMING', NOW()),
                                                                                                  -- (4, 4, 'Classical Music Concert',
                                                                                                  --  'A sophisticated evening of classical music performed by renowned orchestral musicians.',
                                                                                                  --  'Classical', '/images/classical-concert.jpg',
                                                                                                  --  '2025-08-20', '2025-08-20', 120, 60000, 'ENDED', NOW()),
                                                                                                  (5, 2, 'Broadway Musical',
                                                                                                   'A spectacular Broadway-style musical featuring incredible vocals and stunning choreography.',
                                                                                                   'Musical', 'performances/posters/20250924/0899cde7.jpg',
                                                                                                   '2025-11-30', '2025-11-30', 165, 95000, 'UPCOMING', NOW()),
                                                                                                  -- (6, 5, 'Chicago The Musical',
                                                                                                  --  '1920년대 시카고를 배경으로 한 재즈 뮤지컬의 걸작.',
                                                                                                  --  'Musical', '/images/chicago-musical.jpg',
                                                                                                  --  '2025-09-15', '2025-09-30', 140, 85000, 'UPCOMING', NOW()),
                                                                                                  -- (7, 6, 'Cats',
                                                                                                  --  'T.S. 엘리엇의 시를 바탕으로 한 세계적인 뮤지컬.',
                                                                                                  --  'Musical', '/images/cats-musical.jpg',
                                                                                                  --  '2025-10-01', '2025-10-31', 150, 90000, 'UPCOMING', NOW()),
                                                                                                  -- (8, 7, 'La Traviata',
                                                                                                  --  '베르디의 대표작 오페라 라 트라비아타.',
                                                                                                  --  'Opera', '/images/la-traviata.jpg',
                                                                                                  --  '2025-11-15', '2025-11-15', 180, 120000, 'UPCOMING', NOW()),
                                                                                                  (9, 1, 'Romeo and Juliet Ballet',
                                                                                                   '프로코피예프의 음악으로 선보이는 로미오와 줄리엣 발레.',
                                                                                                   'Ballet', 'performances/posters/20250924/4098b64f.jpg',
                                                                                                   '2025-12-01', '2025-12-01', 160, 70000, 'UPCOMING', NOW()),
                                                                                                  (10, 3, 'K-Pop Festival 2025',
                                                                                                   '국내 최고의 K-Pop 아티스트들이 한자리에 모이는 대축제.',
                                                                                                   'Concert', 'performances/posters/20250924/5e93bdfb.jpg',
                                                                                                   '2025-08-15', '2025-08-15', 300, 150000, 'ENDED', NOW()),
                                                                                                  (11, 8, 'Chamber Music Recital',
                                                                                                   '소규모 실내악 앙상블의 아름다운 선율이 울려퍼지는 특별한 연주회.',
                                                                                                   'Classical', 'performances/posters/20250924/c070dee7.jpg',
                                                                                                   '2025-10-25', '2025-10-25', 90, 45000, 'UPCOMING', NOW()),
                                                                                                  (12, 9, 'Experimental Theater',
                                                                                                   '새로운 형식의 실험적 연극으로 관객과의 소통을 중시하는 혁신적인 작품.',
                                                                                                   'Experimental', 'performances/posters/20250924/c4038e2f.jpg',
                                                                                                   '2025-11-10', '2025-11-10', 60, 25000, 'UPCOMING', NOW())
    ON CONFLICT (performance_id) DO NOTHING;

-- ============================================
-- PERFORMANCE_SCHEDULES (SCHEDULED→OPEN, COMPLETED→SOLDOUT(available=0))
-- ============================================
INSERT INTO performance_schedules (schedule_id, performance_id, show_datetime,
                                   total_seats, available_seats, status, booking_start_at, booking_end_at, created_at) VALUES
                                                                                                                           (1, 1, '2025-10-15 19:00:00', 1200, 1196, 'OPEN', NOW(), '2025-10-15 18:00:00', NOW()),
                                                                                                                           (2, 2, '2025-09-07 19:30:00', 1500, 1499, 'OPEN', NOW(), '2025-09-07 18:00:00', NOW()),
                                                                                                                           (3, 3, '2025-12-21 19:00:00', 2000, 1996, 'OPEN', NOW(), '2025-12-21 18:00:00', NOW()),
                                                                                                                           (5, 5, '2025-11-30 20:00:00', 1500, 1500, 'OPEN', NOW(), '2025-11-30 18:00:00', NOW()),
                                                                                                                           (13, 9, '2025-12-01 15:00:00', 1200, 1199, 'OPEN', NOW(), '2025-12-01 14:00:00', NOW()),
                                                                                                                           (14, 9, '2025-12-01 19:00:00', 1200, 1200, 'OPEN', NOW(), '2025-12-01 18:00:00', NOW()),
                                                                                                                           (15, 10, '2025-08-15 18:00:00', 2000, 0, 'SOLDOUT', NOW(), '2025-08-15 17:00:00', NOW()),
                                                                                                                           -- 100석 공연장 (venue_id = 8) 공연 일정
                                                                                                                           (16, 11, '2025-10-25 19:30:00', 100, 100, 'OPEN', NOW(), '2025-10-25 18:30:00', NOW()),
                                                                                                                           (17, 11, '2025-10-26 15:00:00', 100, 100, 'OPEN', NOW(), '2025-10-26 14:00:00', NOW()),
                                                                                                                           -- 10석 공연장 (venue_id = 9) 공연 일정
                                                                                                                           (18, 12, '2025-11-10 19:00:00', 10, 10, 'OPEN', NOW(), '2025-11-10 18:00:00', NOW()),
                                                                                                                           (19, 12, '2025-11-11 19:00:00', 10, 10, 'OPEN', NOW(), '2025-11-11 18:00:00', NOW()),
                                                                                                                           (20, 12, '2025-11-12 19:00:00', 10, 10, 'OPEN', NOW(), '2025-11-12 18:00:00', NOW())
    ON CONFLICT (schedule_id) DO NOTHING;

-- ============================================
-- SCHEDULE_SEATS (새로운 구조)
-- sparse overlay 방식: venue의 total_capacity에서 실제 등록된 좌석만 판매
-- ============================================
--
-- -- schedule 1 (Phantom / Grand Opera House)
-- INSERT INTO schedule_seats (seat_id, schedule_id, grade, zone, row_label, col_num, price, status, created_at) VALUES
--                                                                                                                 (1, 1, 'VIP', 'A', 'A', '5', 75000, 'BOOKED', NOW()),
--                                                                                                                 (2, 1, 'VIP', 'A', 'A', '6', 75000, 'BOOKED', NOW()),
--                                                                                                                 (3, 1, 'VIP', 'A', 'A', '7', 75000, 'AVAILABLE', NOW()),
--                                                                                                                 (4, 1, 'VIP', 'A', 'A', '8', 75000, 'AVAILABLE', NOW()),
--                                                                                                                 (5, 1, 'R', 'B', 'B', '5', 65000, 'AVAILABLE', NOW()),
--                                                                                                                 (6, 1, 'R', 'B', 'B', '6', 65000, 'AVAILABLE', NOW())
--     ON CONFLICT (seat_id) DO NOTHING;
--
-- -- schedule 2 (Swan Lake / National Theater)
-- INSERT INTO schedule_seats (seat_id, schedule_id, grade, zone, row_label, col_num, price, status, created_at) VALUES
--                                                                                                                 (7, 2, 'VIP', 'A', 'A', '1', 65000, 'AVAILABLE', NOW()),
--                                                                                                                 (8, 2, 'VIP', 'A', 'A', '2', 65000, 'AVAILABLE', NOW()),
--                                                                                                                 (9, 2, 'R', 'B', 'B', '12', 65000, 'BOOKED', NOW()),
--                                                                                                                 (10, 2, 'R', 'B', 'B', '13', 65000, 'AVAILABLE', NOW()),
--                                                                                                                 (11, 2, 'R', 'F', 'F', '14', 65000, 'AVAILABLE', NOW()),
--                                                                                                                 (12, 2, 'R', 'F', 'F', '15', 65000, 'AVAILABLE', NOW())
--     ON CONFLICT (seat_id) DO NOTHING;
--
-- -- schedule 3 (Rock Concert / Arena Stadium)
-- INSERT INTO schedule_seats (seat_id, schedule_id, grade, zone, row_label, col_num, price, status, created_at) VALUES
--                                                                                                                 (13, 3, 'VIP', 'A', 'A', '1', 150000, 'AVAILABLE', NOW()),
--                                                                                                                 (14, 3, 'VIP', 'A', 'A', '2', 150000, 'AVAILABLE', NOW()),
--                                                                                                                 (15, 3, 'S', 'C', 'C', '15', 80000, 'LOCKED', NOW()),
--                                                                                                                 (16, 3, 'S', 'C', 'C', '16', 80000, 'LOCKED', NOW()),
--                                                                                                                 (17, 3, 'S', 'C', 'C', '17', 80000, 'LOCKED', NOW()),
--                                                                                                                 (18, 3, 'S', 'C', 'C', '18', 80000, 'LOCKED', NOW()),
--                                                                                                                 (19, 3, 'A', 'D', 'D', '1', 60000, 'AVAILABLE', NOW()),
--                                                                                                                 (20, 3, 'A', 'D', 'D', '2', 60000, 'AVAILABLE', NOW())
--     ON CONFLICT (seat_id) DO NOTHING;
--
-- -- schedule 5 (Broadway / National Theater)
-- INSERT INTO schedule_seats (seat_id, schedule_id, grade, zone, row_label, col_num, price, status, created_at) VALUES
--                                                                                                                 (25, 5, 'R', 'F', 'F', '14', 95000, 'AVAILABLE', NOW()),
--                                                                                                                 (26, 5, 'R', 'F', 'F', '15', 95000, 'AVAILABLE', NOW())
--     ON CONFLICT (seat_id) DO NOTHING;
--
-- -- schedule 13 (Romeo / Grand Opera House)
-- INSERT INTO schedule_seats (seat_id, schedule_id, grade, zone, row_label, col_num, price, status, created_at) VALUES
--                                                                                                                 (27, 13, 'VIP', 'A', 'A', '10', 70000, 'BOOKED', NOW())
--     ON CONFLICT (seat_id) DO NOTHING;
--
-- -- schedule 14 (Romeo / Grand Opera House)
-- INSERT INTO schedule_seats (seat_id, schedule_id, grade, zone, row_label, col_num, price, status, created_at) VALUES
--                                                                                                                 (28, 14, 'VIP', 'A', 'A', '11', 70000, 'AVAILABLE', NOW())
--     ON CONFLICT (seat_id) DO NOTHING;
--
-- -- schedule 15 (K-Pop Festival / Arena Stadium)
-- INSERT INTO schedule_seats (seat_id, schedule_id, grade, zone, row_label, col_num, price, status, created_at) VALUES
--                                                                                                                 (29, 15, 'VIP', 'A', 'A', '3', 150000, 'BOOKED', NOW()),
--                                                                                                                 (30, 15, 'VIP', 'A', 'A', '4', 150000, 'BOOKED', NOW())
--     ON CONFLICT (seat_id) DO NOTHING;
-- ============================================
-- BOOKINGS
-- ============================================
-- INSERT INTO bookings (booking_id, booking_number, user_id, schedule_id, seat_count, total_amount, status,
--                       expires_at, booked_at, cancelled_at, cancellation_reason, created_at) VALUES
--                                                                                     (1, 'BKG-20250001', 2, 1, 2, 150000, 'CONFIRMED', NULL, '2025-09-01 10:30:00+09', NULL, NULL, NOW()),
--                                                                                     (2, 'BKG-20250002', 2, 2, 1, 65000,  'CONFIRMED', NULL, '2025-08-15 14:20:00+09', NULL, NULL, NOW())
--                                                                                     (3, 'BKG-20250003', 2, 3, 4, 320000, 'PENDING',   '2025-09-11 17:15:00+09', '2025-09-05 16:45:00+09', NULL, NULL, NOW()),
--                                                                                     -- (4, 'BKG-20250004', 2, 4, 4, 220000, 'CONFIRMED', NULL, '2025-08-01 09:15:00+09', NULL, NULL, NOW()),
--                                                                                     (5, 'BKG-20250005', 2, 5, 1, 95000,  'CANCELLED', NULL, '2025-08-25 11:00:00+09', '2025-09-02 15:30:00+09', 'Schedule Conflict', NOW()),
--                                                                                     -- (6, 'BKG-20250006', 3, 6, 1, 85000,  'CONFIRMED', NULL, '2025-08-20 15:45:00+09', NULL, NULL, NOW()),
--                                                                                     -- (7, 'BKG-20250007', 4, 9, 2, 180000, 'CONFIRMED', NULL, '2025-08-25 11:20:00+09', NULL, NULL, NOW()),
--                                                                                     (8, 'BKG-20250008', 5, 1, 1, 75000,  'PENDING',   '2025-09-11 15:00:00+09', '2025-09-06 14:30:00+09', NULL, NULL, NOW()),
--                                                                                     (9, 'BKG-20250009', 6, 2, 2, 130000, 'CONFIRMED', NULL, '2025-08-30 16:15:00+09', NULL, NULL, NOW()),
--                                                                                     (10, 'BKG-20250010', 3, 15, 2, 300000, 'CONFIRMED', NULL, '2025-07-20 12:00:00+09', NULL, NULL, NOW()),
--                                                                                     -- (11, 'BKG-20250011', 4, 16, 1, 80000, 'PENDING',   '2025-09-11 10:00:00+09', '2025-09-07 09:30:00+09', NULL, NULL, NOW()),
--                                                                                     -- (12, 'BKG-20250012', 5, 17, 2, 130000, 'CONFIRMED', NULL, '2025-09-01 18:45:00+09', NULL, NULL, NOW()),
--                                                                                     -- (13, 'BKG-20250013', 6, 12, 1, 120000, 'PENDING',  '2025-09-11 21:45:00+09', '2025-09-06 20:15:00+09', NULL, NULL, NOW()),
--                                                                                     (14, 'BKG-20250014', 7, 13, 1, 70000, 'CONFIRMED', NULL, '2025-08-28 13:20:00+09', NULL, NULL, NOW()),
--                                                                                     (15, 'BKG-20250015', 8, 14, 1, 70000, 'CONFIRMED', NULL, '2025-09-02 10:45:00+09', NULL, NULL, NOW())
--     ON CONFLICT (booking_id) DO NOTHING;
-- -- ============================================
-- -- BOOKING_SEATS
-- -- ============================================
-- INSERT INTO booking_seats (booking_seat_id, booking_id, seat_id, seat_price, created_at) VALUES
--                                                                                              (1, 1, 1, 75000, NOW()),
--                                                                                              (2, 1, 2, 75000, NOW()),
--                                                                                              (3, 2, 9, 65000, NOW()),
--                                                                                              (4, 3, 15, 80000, NOW()),
--                                                                                              (5, 3, 16, 80000, NOW()),
--                                                                                              (6, 3, 17, 80000, NOW()),
--                                                                                              (7, 3, 18, 80000, NOW()),
--                                                                                              -- (8, 4, 21, 60000, NOW()),
--                                                                                              -- (9, 4, 22, 60000, NOW()),
--                                                                                              -- (10, 4, 23, 50000, NOW()),
--                                                                                              -- (11, 4, 24, 50000, NOW()),
--                                                                                              (12, 5, 25, 95000, NOW()),
--                                                                                              -- (13, 6, 27, 85000, NOW()),
--                                                                                              -- (14, 7, 31, 90000, NOW()),
--                                                                                              -- (15, 7, 32, 90000, NOW()),
--                                                                                              (16, 10, 29, 150000, NOW()), -- K-Pop Festival VIP seats
--                                                                                              (17, 10, 30, 150000, NOW()),
--                                                                                              (18, 14, 27, 70000, NOW()),
--                                                                                              (19, 15, 28, 70000, NOW())
--     ON CONFLICT (booking_seat_id) DO NOTHING;

-- -- ============================================
-- -- PAYMENTS (CONFIRMED → COMPLETED)
-- -- ============================================
-- INSERT INTO payments (payment_id, booking_id, transaction_id, amount, payment_method, status, pg_response, paid_at, created_at) VALUES
--                                                                                                                                     (1, 1, 'TX-PHANTOM-0001', 150000, 'CARD', 'COMPLETED', '{"result":"success","tid":"KG20250901103100"}', '2025-09-01 10:31:00', NOW()),
--                                                                                                                                     (2, 2, 'TX-SWAN-0002', 65000, 'KAKAO_PAY', 'COMPLETED', '{"result":"success","tid":"KAKAO20250815142100"}', '2025-08-15 14:21:00', NOW()),
--                                                                                                                                     -- (3, 4, 'TX-CLASSIC-0004', 220000, 'CARD', 'COMPLETED', '{"result":"success","tid":"KG20250801091600"}', '2025-08-01 09:16:00', NOW()),
--                                                                                                                                     (4, 5, 'TX-BROADWAY-0005', 95000, 'TOSS_PAY', 'COMPLETED', '{"result":"success","tid":"TOSS20250825110100"}', '2025-08-25 11:01:00', NOW()),
--                                                                                                                                     -- (5, 6, 'TX-CHICAGO-0006', 85000, 'NAVER_PAY', 'COMPLETED', '{"result":"success","tid":"NAVER20250820154500"}', '2025-08-20 15:46:00', NOW()),
--                                                                                                                                     -- (6, 7, 'TX-CATS-0007', 180000, 'CARD', 'COMPLETED', '{"result":"success","tid":"KG20250825112000"}', '2025-08-25 11:21:00', NOW()),
--                                                                                                                                     (7, 9, 'TX-SWAN2-0009', 130000, 'BANK', 'COMPLETED', '{"result":"success","tid":"BANK20250830161500"}', '2025-08-30 16:16:00', NOW()),
--                                                                                                                                     (8, 10, 'TX-KPOP-0010', 300000, 'CARD', 'COMPLETED', '{"result":"success","tid":"KG20250720120000"}', '2025-07-20 12:01:00', NOW()),
--                                                                                                                                     -- (9, 12, 'TX-HAMLET-0012', 130000, 'KAKAO_PAY', 'COMPLETED', '{"result":"success","tid":"KAKAO20250901184500"}', '2025-09-01 18:46:00', NOW()),
--                                                                                                                                     (10, 14, 'TX-ROMEO-0014', 70000, 'TOSS_PAY', 'COMPLETED', '{"result":"success","tid":"TOSS20250828132000"}', '2025-08-28 13:21:00', NOW()),
--                                                                                                                                     (11, 15, 'TX-ROMEO2-0015', 70000, 'CARD', 'COMPLETED', '{"result":"success","tid":"KG20250902104500"}', '2025-09-02 10:46:00', NOW())
--     ON CONFLICT (payment_id) DO NOTHING;
--
-- -- ============================================
-- -- REFUNDS (booking 5 환불 완료)
-- -- ============================================
-- INSERT INTO refunds (refund_id, payment_id, booking_id, refund_amount, reason, status, requested_at, processed_at, created_at) VALUES
--     (1, 4, 5, 95000, 'Schedule Conflict', 'COMPLETED', '2025-09-02 15:30:00', '2025-09-02 16:00:00', NOW())
--     ON CONFLICT (refund_id) DO NOTHING;
--
-- -- ============================================
-- -- SEAT_LOCKS (booking 3의 LOCKED 좌석 15~18 예시)
-- -- ============================================
-- INSERT INTO seat_locks (lock_id, seat_id, user_id, session_id, locked_at, expires_at, status, created_at) VALUES
--                                                                                                               (1, 15, 2, 'sess-rc-0001', '2025-09-11 14:25:00+09', '2025-09-11 14:35:00+09', 'ACTIVE', NOW()),
--                                                                                                               (2, 16, 2, 'sess-rc-0001', '2025-09-11 14:25:00+09', '2025-09-11 14:35:00+09', 'ACTIVE', NOW()),
--                                                                                                               (3, 17, 2, 'sess-rc-0001', '2025-09-11 14:25:00+09', '2025-09-11 14:35:00+09', 'ACTIVE', NOW()),
--                                                                                                               (4, 18, 2, 'sess-rc-0001', '2025-09-11 14:25:00+09', '2025-09-11 14:35:00+09', 'ACTIVE', NOW()),
--                                                                                                               (5, 3, 5, 'sess-phantom-0002', '2025-09-11 16:42:00+09', '2025-09-11 16:57:00+09', 'ACTIVE', NOW())
--                                                                                                               -- (6, 35, 6, 'sess-opera-0003', '2025-09-11 19:18:00+09', '2025-09-11 19:30:00+09', 'ACTIVE', NOW()),
--                                                                                                               -- (7, 36, 4, 'sess-beethoven-0004', '2025-09-11 21:07:00+09', '2025-09-11 21:15:00+09', 'ACTIVE', NOW())
--     ON CONFLICT (lock_id) DO NOTHING;

-- ============================================
-- SYSTEM_METRICS
-- ============================================
INSERT INTO system_metrics (timestamp, active_users, queue_length, cpu_usage, memory_usage, request_count, avg_response_time, server_id, created_at) VALUES
                                                                                                                                                         (NOW() - INTERVAL '2 hours', 8500, 12, 35.8, 52.3, 4521, 0.095, 'web-srv-1', NOW()),
                                                                                                                                                         (NOW() - INTERVAL '1 hour', 12450, 5, 42.3, 65.8, 7234, 0.145, 'web-srv-1', NOW()),




-- ============================================
-- 시퀀스 리셋 (수동 INSERT 후 필수)
-- ============================================
-- users 테이블은 VARCHAR PRIMARY KEY이므로 시퀀스가 없음
SELECT setval('venues_venue_id_seq', (SELECT COALESCE(MAX(venue_id), 1) FROM venues), true);
SELECT setval('performances_performance_id_seq', (SELECT COALESCE(MAX(performance_id), 1) FROM performances), true);
SELECT setval('performance_schedules_schedule_id_seq', (SELECT COALESCE(MAX(schedule_id), 1) FROM performance_schedules), true);
SELECT setval('schedule_seats_seat_id_seq', (SELECT COALESCE(MAX(seat_id), 1) FROM schedule_seats), true);
SELECT setval('bookings_booking_id_seq', (SELECT COALESCE(MAX(booking_id), 1) FROM bookings), true);
SELECT setval('booking_seats_booking_seat_id_seq', (SELECT COALESCE(MAX(booking_seat_id), 1) FROM booking_seats), true);
SELECT setval('seat_locks_lock_id_seq', (SELECT COALESCE(MAX(lock_id), 1) FROM seat_locks), true);
SELECT setval('payments_payment_id_seq', (SELECT COALESCE(MAX(payment_id), 1) FROM payments), true);
SELECT setval('refunds_refund_id_seq', (SELECT COALESCE(MAX(refund_id), 1) FROM refunds), true);
SELECT setval('system_metrics_metric_id_seq', (SELECT COALESCE(MAX(metric_id), 1) FROM system_metrics), true);
SELECT setval('queue_tokens_token_id_seq', (SELECT COALESCE(MAX(token_id), 1) FROM queue_tokens), true);