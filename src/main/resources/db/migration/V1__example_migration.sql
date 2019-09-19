create sequence id_sequence;

CREATE or replace FUNCTION next_id(OUT "result" bpchar) RETURNS "bpchar"
AS $BODY$
DECLARE
    seq_id bigint;
    calc bigint;
    foo text;
BEGIN
    SELECT nextval('id_sequence') INTO seq_id;
    calc = seq_id << 16;
    result = public.stringify_bigint(calc);
END;
$BODY$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.stringify_bigint(n int8) RETURNS "text"
AS $BODY$
DECLARE
    alphabet text:='BCDFGHJKLMNPQRSTVWXYZ123456789';
    alpha char[];
    base int:=char_length(alphabet);
    rem bigint := 0;
    output text:='';
BEGIN
    alpha := string_to_array(alphabet, null);
    IF n = 0 THEN RETURN alpha[0]; END IF;
    LOOP
        rem := n % base;
        n := (n / base)::bigint;
        output := alpha[rem+1] || output;
        EXIT WHEN n = 0;
    END LOOP;
    RETURN output;
END $BODY$
    LANGUAGE plpgsql;

CREATE TABLE inventories (
    id varchar(16) not null default next_id() primary key,
    data jsonb not null
);

