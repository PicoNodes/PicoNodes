--
-- PostgreSQL database dump
--

-- Dumped from database version 10.4
-- Dumped by pg_dump version 10.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: downloaders; Type: TABLE; Schema: public; Owner: piconodes
--

CREATE TABLE public.downloaders (
    id uuid DEFAULT public.gen_random_uuid() NOT NULL,
    label text
);


ALTER TABLE public.downloaders OWNER TO piconodes;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: piconodes
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO piconodes;

--
-- Name: source_file_revisions; Type: TABLE; Schema: public; Owner: piconodes
--

CREATE TABLE public.source_file_revisions (
    id uuid DEFAULT public.gen_random_uuid() NOT NULL,
    file uuid NOT NULL,
    content text,
    created_at timestamp without time zone NOT NULL
);


ALTER TABLE public.source_file_revisions OWNER TO piconodes;

--
-- Name: source_files; Type: TABLE; Schema: public; Owner: piconodes
--

CREATE TABLE public.source_files (
    id uuid DEFAULT public.gen_random_uuid() NOT NULL,
    name text NOT NULL
);


ALTER TABLE public.source_files OWNER TO piconodes;

--
-- Name: source_files_current; Type: VIEW; Schema: public; Owner: piconodes
--

CREATE VIEW public.source_files_current AS
 SELECT rev.file,
    rev.id AS revision
   FROM ( SELECT DISTINCT ON (rev_1.file) rev_1.id,
            rev_1.file,
            rev_1.content
           FROM public.source_file_revisions rev_1
          ORDER BY rev_1.file, rev_1.created_at DESC) rev
  WHERE (rev.content IS NOT NULL);


ALTER TABLE public.source_files_current OWNER TO piconodes;

--
-- Data for Name: downloaders; Type: TABLE DATA; Schema: public; Owner: piconodes
--

COPY public.downloaders (id, label) FROM stdin;
96517e25-839c-432b-b24e-f98b97575570	Main
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: piconodes
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	Initial setup	SQL	V1__Initial_setup.sql	-1727840944	piconodes	2018-05-16 11:25:58.012899	22	t
2	2	Files	SQL	V2__Files.sql	824522041	piconodes	2018-05-16 11:25:58.044539	43	t
3	3	File View	SQL	V3__File_View.sql	238765689	piconodes	2018-05-16 11:25:58.096061	2	t
\.


--
-- Data for Name: source_file_revisions; Type: TABLE DATA; Schema: public; Owner: piconodes
--

COPY public.source_file_revisions (id, file, content, created_at) FROM stdin;
99a8d3e8-bf13-4d42-98ef-2fe684ab2a5e	6f04bbc9-acba-472f-86c1-3617eb8bb3d5	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n	2018-05-16 11:27:05.378
5a69e353-29aa-4dd9-8297-b3076bdbc293	6f04bbc9-acba-472f-86c1-3617eb8bb3d5	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n	2018-05-16 11:27:11.113
7cb280b1-cad3-47f3-b173-6c46c6501cc3	d1650474-8895-4bc8-8203-9a832d70d187		2018-05-16 11:27:18.464
6745d0aa-b974-48b7-864a-acc0ed3b56ef	02550564-bc3d-4e7f-86ae-838d485ed3cd		2018-05-16 11:28:30.431
a93dc312-82f6-43a4-aced-32591ef6b14c	6f04bbc9-acba-472f-86c1-3617eb8bb3d5	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n  asdfqwre	2018-05-16 11:28:58.791
f5c00ecf-9d46-4622-85b5-3204b3758535	6f04bbc9-acba-472f-86c1-3617eb8bb3d5	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n  	2018-05-16 11:29:10.626
399c47b1-a29c-4ab0-a642-a8686add61af	6f04bbc9-acba-472f-86c1-3617eb8bb3d5	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n  asdf	2018-05-16 11:29:18.439
a6baabc0-b30e-46f6-b4f4-b6612cf5f782	02550564-bc3d-4e7f-86ae-838d485ed3cd	\N	2018-05-16 11:31:50.583916
bbe7a399-0ba7-4e88-94ac-0c97d3009ce8	87620b55-2d5b-4a7b-bc9f-0f9a25912264	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n	2018-05-16 11:40:38.313
aed14187-2d47-4035-8e82-e902bd7dd667	87620b55-2d5b-4a7b-bc9f-0f9a25912264	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n	2018-05-16 11:41:34.742
9d486bf9-0980-4fcc-a637-39e5c828921d	87620b55-2d5b-4a7b-bc9f-0f9a25912264	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n  	2018-05-16 11:41:43.216
24fbc9f0-bdfa-49a9-bf66-78050057237e	87620b55-2d5b-4a7b-bc9f-0f9a25912264	  mov 1 up\n  mov 2 null\n  mov 3 acc\n  \n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n  	2018-05-16 11:41:48.693
9482b30b-0179-40b4-b8bf-9def6be3586e	87620b55-2d5b-4a7b-bc9f-0f9a25912264	  mov 1 up\n  mov 2 null\n  mov 3 acc\n  \n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n  asdf	2018-05-16 12:42:20.186
7d96d340-3fea-4a00-9ed3-38825fb37b12	d1650474-8895-4bc8-8203-9a832d70d187	qwer	2018-05-16 12:42:25.865
829322c8-b1e3-4ba8-983d-c703d8aeb4b9	014c56b2-970e-4873-a6b9-d7ae3342c1c2	  mov 1 up\n  mov 2 null\n  mov 3 acc\n\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\nqwer	2018-05-16 12:48:11.81
539a3284-c767-4e6b-a658-eb43bf6c8396	7ec89c9c-be02-4077-9eb3-88cb333aac31	  mov 1 up\n  mov 2 null\n  mov 3 acc\nqwer\n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n	2018-05-16 13:06:41.075
d55fd55f-10de-4530-9c86-8d97b539b472	87620b55-2d5b-4a7b-bc9f-0f9a25912264	  mov 1 up\n  mov 2 null\n  mov 3 acc\n  qwer\n  \n+ mov 4 acc\n- mov 6 acc\n  mov 5 null\n  asdf	2018-05-16 15:29:51.752
cdb6c33e-5f14-4aee-8162-34370785aac9	01a2b812-cc31-40e1-b70b-5e3cdc60c14f	  tcp left 0\n+ mov acc right\n+ teq acc 4\n+ mov 1 acc\n- add 1	2018-05-18 16:13:09.716
95f564c3-14a1-4a93-9844-ba0c5c9f10ba	01a2b812-cc31-40e1-b70b-5e3cdc60c14f	  tcp left 0\n+ mov acc right\n+ teq acc 4\n+ mov 1 acc\n- add 1	2018-05-18 16:13:42.654
dea72aba-197d-403f-a2c3-109ab2d61389	01a2b812-cc31-40e1-b70b-5e3cdc60c14f	  teq acc 0\n+ mov 1 acc\n+ mov 1 right\n  tcp left 0\n+ mov acc right\n+ teq acc 4\n+ mov 1 acc\n- add 1	2018-05-21 13:43:18.143
2f00142e-5f16-46a1-9c97-0f24ef9958a2	d13122a4-c24f-4a5f-82de-5e897b453a63	  teq acc left\n- mov left acc\n- mov acc right	2018-05-21 14:05:32.54
1017b918-ccac-476d-80d7-903a1eb83ace	01a2b812-cc31-40e1-b70b-5e3cdc60c14f	  tcp left 0\n+ mov acc right\n+ teq acc 4\n+ mov 1 acc\n- add 1	2018-05-21 22:03:15.46
c12f5adf-091b-4eaa-9e40-50b3521c2120	d13122a4-c24f-4a5f-82de-5e897b453a63	  mov 4 right	2018-05-21 22:22:10.983
f9f24c62-397a-4525-8e02-c8e9bc8d342b	3a0f05f5-4a09-4973-b025-ed6618604de8	  teq left 3\n+ mov 1 right\n- mov 3 right	2018-05-21 22:28:07.598
8d5c9d5f-e219-410d-a80e-be1c9bd284cf	d8ae1ee0-6ae6-4f2b-a8ed-7f527bc8e34e	  tcp left 0\n+ mov acc right\n+ teq acc 4\n+ mov 1 acc\n- add 1	2018-05-21 23:07:17.01
5e21a38c-33b5-4756-99e5-e72d79acb2a1	d8ae1ee0-6ae6-4f2b-a8ed-7f527bc8e34e	  teq acc 4\n+ mov 1 acc\n- add 1\n  mov acc right	2018-05-21 23:07:33.148
c343886c-7490-4ccc-ad8d-113e74230a6a	01a2b812-cc31-40e1-b70b-5e3cdc60c14f	  tcp left null\n+ mov acc right\n+ teq acc 4\n+ mov 1 acc\n- add 1	2018-05-21 23:14:04.729
67c35c6b-55ad-416d-91d4-200a22663cf2	01a2b812-cc31-40e1-b70b-5e3cdc60c14f	  tcp left 0\n+ mov acc right\n+ teq acc 4\n+ mov 1 acc\n- add 1	2018-05-21 23:31:25.787
\.


--
-- Data for Name: source_files; Type: TABLE DATA; Schema: public; Owner: piconodes
--

COPY public.source_files (id, name) FROM stdin;
02550564-bc3d-4e7f-86ae-838d485ed3cd	Argh
6f04bbc9-acba-472f-86c1-3617eb8bb3d5	Test
d1650474-8895-4bc8-8203-9a832d70d187	Test 2
014c56b2-970e-4873-a6b9-d7ae3342c1c2	Example
7ec89c9c-be02-4077-9eb3-88cb333aac31	Example
87620b55-2d5b-4a7b-bc9f-0f9a25912264	Exampleasdf
d13122a4-c24f-4a5f-82de-5e897b453a63	Flashlight
3a0f05f5-4a09-4973-b025-ed6618604de8	TEQ TEST
d8ae1ee0-6ae6-4f2b-a8ed-7f527bc8e34e	Light 'em all
01a2b812-cc31-40e1-b70b-5e3cdc60c14f	Dumb RNG
\.


--
-- Name: downloaders downloaders_pkey; Type: CONSTRAINT; Schema: public; Owner: piconodes
--

ALTER TABLE ONLY public.downloaders
    ADD CONSTRAINT downloaders_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: piconodes
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: source_file_revisions source_file_revisions_pkey; Type: CONSTRAINT; Schema: public; Owner: piconodes
--

ALTER TABLE ONLY public.source_file_revisions
    ADD CONSTRAINT source_file_revisions_pkey PRIMARY KEY (id);


--
-- Name: source_files source_files_pkey; Type: CONSTRAINT; Schema: public; Owner: piconodes
--

ALTER TABLE ONLY public.source_files
    ADD CONSTRAINT source_files_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: piconodes
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: source_file_revisions source_file_revisions_file_fkey; Type: FK CONSTRAINT; Schema: public; Owner: piconodes
--

ALTER TABLE ONLY public.source_file_revisions
    ADD CONSTRAINT source_file_revisions_file_fkey FOREIGN KEY (file) REFERENCES public.source_files(id);


--
-- PostgreSQL database dump complete
--

