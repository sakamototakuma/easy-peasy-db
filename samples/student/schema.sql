create table dept (
    did    int,
    dname  varchar(8)
);

create table student (
    sid       int,
    sname     varchar(10),
    majorid   int,
    gradyear  int
);

create table course (
    cid     int,
    title   varchar(20),
    deptid  int
);

create table sect (
    sectid       int,
    courseid     int,
    prof         varchar(8),
    yearoffered  int
);

create table enroll (
    eid        int,
    studentid  int,
    sectionid  int,
    grade      varchar(2)
);