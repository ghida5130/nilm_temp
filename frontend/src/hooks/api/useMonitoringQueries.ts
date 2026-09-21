import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { getMyDashboard, getPowerUsage, getSubjectEvents, getSubjects } from "../../api/monitoring";
import { monitoringKeys } from "./monitoringKeys";

export function useSubjectsQuery() {
  return useQuery({
    queryKey: monitoringKeys.subjects,
    queryFn: getSubjects,
    refetchInterval: 60_000,
  });
}

export function useMyDashboardQuery() {
  return useQuery({
    queryKey: monitoringKeys.myDashboard,
    queryFn: getMyDashboard,
    refetchInterval: 30_000,
  });
}

export function useSubjectEventsQuery(subjectId: string, version: number) {
  return useInfiniteQuery({
    queryKey: [...monitoringKeys.events(subjectId), version],
    queryFn: ({ pageParam }) => getSubjectEvents(subjectId, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
  });
}

export function usePowerUsageQuery(subjectId: string, date: string, version: number) {
  return useQuery({
    queryKey: [...monitoringKeys.power(subjectId, date), version],
    queryFn: () => getPowerUsage(subjectId, date),
    enabled: Boolean(date),
  });
}
