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

create index idx_dept_did        on dept(did);
create index idx_student_sid     on student(sid);
create index idx_course_cid      on course(cid);
create index idx_sect_sectid     on sect(sectid);
create index idx_enroll_eid      on enroll(eid);

create index idx_student_majorid   on student(majorid);
create index idx_course_deptid     on course(deptid);
create index idx_sect_courseid     on sect(courseid);
create index idx_enroll_studentid  on enroll(studentid);
create index idx_enroll_sectionid  on enroll(sectionid);

create index idx_student_gradyear   on student(gradyear);
create index idx_sect_yearoffered   on sect(yearoffered);
