import {
  DEFAULT_LISTING_FILTERS,
  parseListingFiltersFromSearchParams,
} from "@/features/listings/api";
import { ListingsPageView } from "@/features/listings/listings-page-view";

type ListingsPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function ListingsPage({ searchParams }: ListingsPageProps) {
  const resolvedSearchParams = (await searchParams) ?? {};
  const initialFilters =
    Object.keys(resolvedSearchParams).length > 0
      ? parseListingFiltersFromSearchParams(resolvedSearchParams)
      : DEFAULT_LISTING_FILTERS;
  return <ListingsPageView initialFilters={initialFilters} />;
}
