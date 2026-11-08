--
-- [EXEMPLE] PostgreSQL database dump (fictional extract of the Dosleg copy: only the tables read)
--

SET client_encoding = 'LATIN1';
SET standard_conforming_strings = off;

CREATE TABLE scr (
    sesann integer NOT NULL,
    scrnum integer NOT NULL
);

COPY sen (senmat, sennomuse, senprenomuse, sendatnai) FROM stdin;
99101A	Castagnet	[EXEMPLE] Hélène	1970-01-01
99102A	Daubrée	[EXEMPLE] Ignace	1970-01-01
99102A	Daubrée	[EXEMPLE] Ignace	1970-01-01
99103A	Escoffier	[EXEMPLE] Jeanne	1970-01-01
99104A	Fabrègue	[EXEMPLE] Kilian	1970-01-01
99105A	Gaubert	[EXEMPLE] Léonie	1970-01-01
\.

COPY posvot (posvotcod, posvotlib) FROM stdin;
p	Pour
c	Contre
a	Abstention
n	N'a pas pris part au vote
\.

COPY scr (sesann, scrnum, scrint, scrdat, scrpou, scrcon, scrvot, scrsuf, scrpousea, scrconsea, scrvotsea, scrsufsea, scrmaj, scrobs) FROM stdin;
2005	12	[EXEMPLE] Scrutin d'une session antérieure à 2006	2005-12-01	200	100	310	300	200	100	310	300	151	\N
2006	9001	[EXEMPLE] l'ensemble du projet de loi relatif aux sentiers côtiers	2006-11-15 00:00:00	200	120	330	320	200	120	330	320	161	\N
2018	9001	[EXEMPLE] l'amendement n° 7 à l'article 2 de la proposition de loi relative aux phares\tdeuxième lecture	2018-10-17	150	170	330	320	150	170	330	320	161	\N
2018	9002	[EXEMPLE] l'ensemble de la proposition de loi relative aux moulins	2019-02-05	250	80	335	330	250	80	335	330	166	\N
2026	9001	[EXEMPLE] l'ensemble du projet de loi de finances pour 2027	2026-10-20	180	140	340	320	180	140	340	320	161	\N
\.

COPY votsen (sesann, scrnum, senmat, posvotcod, senmatdel, stavotidt) FROM stdin;
2005	12	99101A	p	\N	1
2006	9001	99101A	p	\N	1
2006	9001	99102A	p	\N	1
2006	9001	99103A	c	\N	1
2018	9001	99101A	p	\N	1
2018	9001	99102A	c	\N	1
2018	9001	99103A	c	\N	1
2018	9001	99104A	a	\N	1
2018	9002	99101A	p	\N	1
2018	9002	99102A	p	\N	1
2018	9002	99103A	n	\N	1
2018	9002	99104A	p	99103A	1
2026	9001	99101A	c	\N	1
2026	9001	99102A	p	\N	1
2026	9001	99103A	p	\N	1
2026	9001	99105A	a	\N	1
\.

--
-- PostgreSQL database dump complete
--
