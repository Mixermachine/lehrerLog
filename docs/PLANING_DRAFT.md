Lehrerlog

The software should make it possible for teachers to track the submissions of home work or other assignments. The teacher creates a class with pupils.
When a new assignment should be handed out to the pupils, the teacher creates a new assignment in the app with a final submission date, link to the assignment pdf and assigns a complete class or only one or many pupils to the assignment. Both should be possible.
The assignment task should be viewable in the app. The PDF view page needs to work on all environments. For the first approach it is OK to use a system PDF viewer. Please keep in mind, this is a multi platform project with a lot of different targets.
Teachers should be able to upload new assignments.
The file size of assignments needs to be limited by the server. 5MB per submission. The standard tier of the software allows 100MB total upload size. The page for upload should read the limits (also the current total upload size available for the teacher account).
An S3-compatible object storage (e.g., MinIO) hosted alongside the app server using Docker on the same server should be used for storage. There should be precautions to only let the teacher and potentially later on the parents of the pupils access these files. A pure URL link for access with no further precautions is not acceptable.

The teachers should be able to "cross-off" pupils from the assignment when they have submitted their work in person.
During the cross-off action it should be possible to for the teacher to give a rating (school grates, 1-6 with decimal) and/or note in the same step).

If a pupil is late for an assignment this needs to be shown in the UI. First on the submission page and then also on the pupil/class page.
Being late should be noted on their profile. The teacher should be able to decide if a late submission is possible and solves the late submission (forgiven) or the late submission is permanent and being tracked with a point system. The teacher should be able to define a global system which tracks how many missed assignment should require a punishment submission. This punishment submission then resets the punishment required counter.
Logic: TotalMissed % Threshold == 0. When true, PunishmentState is required.
The total count of missed assignments still needs to be visible for the mid and end of year evaluation. This is a very important feature and needs to be displayed in a nice way. Possibly with graphs.

As classes move every year one step up, renaming a class should be possible. Not all pupils always step up with the class. Moving pupils to another class or deleting them should be possible.

Planed feature for later on:
Parents often want to know what assignments their kids have.
The same app should have a parents user mode which only displays the assignments for their kids (zero or many). No other kids information should be shown. They also should be able to view the assignment pdf and the missed submissions count but should never be able to changed data or to upload anything.

ALL features need to be tested.
First with target generic ExperimentalTestApi Compose Multiplatform Tests.
Second with Roborazzi tests.
