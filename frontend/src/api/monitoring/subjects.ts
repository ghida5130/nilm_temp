import type { Events, Power, Subject, SubjectRegistration } from "../../types/monitoring";
import { request } from "../client";

export type SubjectsResponse = { subjects: Subject[] };

export const getSubjects = () => request<SubjectsResponse>({ url: "/monitoring/dashboard" });

export const registerSubject = (subject: SubjectRegistration) =>
  request<void>({
    url: "/monitoring/subjects",
    method: "POST",
    data: subject,
  });

export const getSubjectEvents = (subjectId: string, cursor?: string) =>
  request<Events>({
    url: `/monitoring/subjects/${subjectId}/events`,
    params: { size: 20, cursor },
  });

export const getPowerUsage = (subjectId: string, date: string) =>
  request<Power>({
    url: `/monitoring/subjects/${subjectId}/power-usage`,
    params: { date },
  });
