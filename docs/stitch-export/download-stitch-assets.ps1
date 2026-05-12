$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$htmlDir = Join-Path $root "html"
$shotDir = Join-Path $root "screenshots"

New-Item -ItemType Directory -Force -Path $htmlDir, $shotDir | Out-Null

$screens = @(
  @{
    Key = "guest-landing-page"
    Screen = "Trang chu - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2VmYTY4YzBlZGY4NzQzYzk5NTdiNjdiOWUxMjljZmYxEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ugStkMZzhFxRn-g59rp8aMvcOb3AeTXhT8pGWYAJpb31zLgBSdc9rRuaubEtIHg7TwMkBBsh3cvl1nAOnlFeWcSK4rJFqLdrDmyOjZCuOPI_KjRdWxfuUBrSsWIu3a7BEmA-7xtqB80HfcWaqs7-XdJrgHhkhTnBkPnBA8RIjfUokKmRQ2np3uIgddd9Q-FHMSOikZblQ9y-lKMlgBvW56fzgeQocFVRA98wlP3lLYN6LcjQXsXsKE6qpwE"
  },
  @{
    Key = "guest-search-results"
    Screen = "Ket qua tim kiem - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzc2ZDFmMDU1Yjk2ZDRiN2ViODRhNzlkNjI5NzkyMmFhEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ugMzWWqPoOrfs2thO29LwwsAEg5R7tm4QwuOXb4PXlQU8EAsZmykAetoUN3FmhWrI5vX0OclPThP5dONPnglweJGcST9kKwHl9YgGUcCL1eG4cOCbGgd5q1Z1xK2Ok2whN2nzBv7XjP1ne3yZMxDmzdjD1hVVZ70IfzANOaidbxM_9bfC0BbGOfv_6sgVZYvktgS-qu9E1RRmJH4JxW3ljLKjznO9xdzX7VBFtiKMVOHa4Tr6ygXWfpliYo"
  },
  @{
    Key = "guest-listing-detail"
    Screen = "Chi tiet xe Meridian - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2FkZmFmMzdlNDE0YTQyMWNhZGViNjZlZTUxODUwZDRiEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ugy9T0lhqLJdg93fiiePl_q7k_2iAExyBF9CL1R6E6oxbtYeEeuuEF4zqIzluq39rVRz-qYkrURZPgvFg6DD9V4wbrZYk0fnvo4XcMjhGiOKjBTd783LN5ZBudG7Wref48IR8AtW39_RvehuKm8rNMsYmsBFBNsOIxLQGuSMykGe3RY1O9b6tMoufNqklc9acEoqtBbcU1Vtnb1M3-uM5e-ybri2DGIDCCQ0nuexkY2t2CNGq2hWhD-QlJ_"
  },
  @{
    Key = "guest-login-error"
    Screen = "Dang nhap loi - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2U2MDNhMjc2ZTBiMDRiZDE4NTBiODY5NTJhNTNjYmE5EgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ugjyedMoezpk6zTY8Nm7U-PXvJ62Ztdhg3wLfFMb8qkWViCPUjWJ-mkmxJQn7YEYnZW8Zw6J-P_lBWtA9ZNFkXhQQoAFHC7S2mBeoNTKhzF3RFSfbHa41PBTKWXF0YlR8xAGVdxtp4jhd_BLxhhOvcF6mZj2mFaZ6-_59obtaGqw6LHLUsggmaafZQJmb1Xb6kabg6aa2K8MdT2TpI7v1cySgOuB2kHu9_PIoGbobQ2DFk_TDq1eZSRNDN9"
  },
  @{
    Key = "guest-register"
    Screen = "Dang ky tai khoan - RentFlow Automotive"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2I4M2NhY2MzYWU4YjQ1NjJiNDcyMjNmMGVmN2Y1MDlmEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uh7zMfH2kOcKzQHf6a3FT1-d325l0Q-_fbAuN_9yHp_uFCXtAXCY4A9ZjrR-b8i5jp5-lWM8rgmKCPwxzkotJKocM6MqMU1D7xy7-xb0eJ5nVdHmOQ4Q73AFUSF-M_vvv4s7gt0ylYjQafxk5ao6f1kjT2Uat656BPFj1BlxJ_vzTS94F8DTHKSfAcvUNES2_NY-NqpLxAAQJKqGcwK3c_9TpVhezZOlktjl7usY2y9_CDcXQEAkUB5JZs"
  },
  @{
    Key = "customer-book-your-ride"
    Screen = "Book Your Ride - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzQ4OWUxMzJiMDNjZTQ5YmZiYzIzZDE3NzM4ODE1MzRjEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uhmMdCtUM8IMzzlOWQI5dAF51l0pMelfBcxWM7vD6tTT6WbkZt4p3D5ir-H9wXUdL_sezsWXqI6fk1gxzpjjloja2EWkvjAhWDpae5VmmprEVffu7hlC_7-NQAJK6OjoM8YUOwvZvdFo_zGAbV9ljCOGNAznYVQ7VEcTFSgcMv8yWXsmufMcxLMJGqtQJKJRe6vqS_pQndG3YNke77f7yGXkZQlvRMNCttBCX-YhckxL5DBeuN8qj--fQQ"
  },
  @{
    Key = "customer-review-book"
    Screen = "Review & Dat xe - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2IwN2M4YTdjNTdmMjQwZmU4MGFmNTRmNWRhMjI5ZWU1EgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uhTE0PdFVBP8Y0MUsLVOiopvgK67ruRlSCUIcWqP8qHJbskYW5cY1P0ROXXFzbINLLo1BOrHEftuiOJK-W7hMEbr40VL_7F02rlIz-pkytHsuJwVNVrzip-aGUB-lkxLWIrWpYp96k8RbSMqmaRcTpx7mF1zC8C4iksbumuwp19rKHFeOIBE-95VVCOQLRIKIds-vfhzu5J9Dsvwvk011h8nGvyRdm6pAHzCc643WIkEGM_U7xKsAroeWX8"
  },
  @{
    Key = "customer-account-settings"
    Screen = "Cai dat tai khoan - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzA2YTdmM2VlMmNlNDQ1MzdhZGUwMDFiNWZkYjkyNzVlEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uj2_ujbu4Qb3ppiNvr48LpehabP7RX-XhuvfbjLN0PcG3-h_f6_h2CdvUHTXBngL5Ml_0ijNhlrwM1FXa5QT7h_tvmVdfMT_6lUwLipswsU-uijGysQFe0x7SjSb4XwpVurcVbueelC5juYPSlVbsUspbQSa22fHOjcVU9PTXYSAixLqbx08QumM1jzzgTxmuT7OH2LGgnmp7y82hpvUxN24mRb2qFZHNZKGyFRD_KkaRcBfLFnGROSoMcP"
  },
  @{
    Key = "customer-booking-details"
    Screen = "Booking Details - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzc2YzE5Y2ZmMjU3ZDRlNTFiMWYyZDI2NWM4ZDJhMDFiEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uh6z9xMEHJV1xU0UmMdxjkDTGfzdDa74EfHMWEgPgHybWwzjX7WA7WhjHgultbFdlTGisYWN85pG5u59Epmu460_B74WarIDCvUu03XWoFdhsTbgsQl_kSssHfbshtV2pHXXwFibhpMYT6Nd1u30CTUlB9FsJClIrXzpwKUHgYwGsvbAqLPLM63iza3eU0msa6T-cZZuY4Q1xF6EIUgBwOWXh904RSmofdz_E44edEGM9rjaRGoqz5ENcnC"
  },
  @{
    Key = "customer-edit-locations-modal"
    Screen = "Edit Locations Modal - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzAwY2JmZWM5ODYyNTRiYjViOWFmMWEwOWEwNWQxODFjEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uhOjSB1SetEj1_MXV7pe95glmYgPrJam0Wodt6CH6oWO3yNoes5wwapaN_nESR6gRZS9soH-Dj9oP01VfubJQOXz4hZTouJL9DRBN8FOo6g2nXJfvcu6t8_NWW-lxnFTGDHgWtAQ3NbFETYwq95MVUdee81f_1ls5SMeOFPJIyVmNgQHTBW8EkFd4aJeze5BL7KwDM6dd1rHuQZO7FAh5N2U5OwkiFN043TaW_5LqZwpuensrldINWwRE7H"
  },
  @{
    Key = "customer-cancel-hold"
    Screen = "Huy giu xe - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzZlNzkwNmE4Mjk1MTRlZjFiNzQ1NmRhOWI4ZWNiNjNhEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uiMpbbjFuh3flmLEPU1xquAd7WCuzK7GJGaKM1sb7dXu4_TYJehEvQ6hz5aVA1vzIFq_vfJVil3C-_KYhmTHZ-5cp2_YLePn5XX7jHtyTGwHHFtBGjeX8bed39tQAlKv7lBhVmZRon_tnLREQwS3c9XpmLYFNrFjS3OlEKmtfiBYgWfRofwkMwnNBvZ4vrz6yf2ZvuqVzRpribdxbJHOUfb8Dd3yN-z4dcO7-SRuEcRHOxwvvWF-T-Qayg"
  },
  @{
    Key = "host-dashboard"
    Screen = "Bang dieu khien - RentFlow Host"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzJjOTg1ZDQ3YmY5NDRmMTBiZjI2MmU2MDZjMTc1YWRlEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uiDMhMPANJ8t2QpcXCndf-_GLjH6v8jTAxf2VE4jZVjoHJIz-B891BsYn-rx0-NHRTl989mSCsnL6T4d8EmFuVGYr5Q4Mwga2h0-637OOuprzWha5FvFAiqjnN0SltIVIjwDvKsAaqE59TGH0HcAKET1C9oyKL3cIfeBM6xY86WshLvkIwWCEXEs7lMbEWOEW9XNn-8a6sy1EGS6THb5SZnenssJ5TLEEecjg5zDlQ01itOc7I9r7GsU5Q"
  },
  @{
    Key = "host-fleet-management"
    Screen = "Quan ly doi xe - RentFlow Host"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2Q2ZWJmZTA1YTgyMTQ1YzNiZWFiMGYzZDNmNTBkMzAzEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ugy4n6Lsytfbb4iPTrlWvm0rc_WVWRIYcYzmTceEbhLXXbktjH53pjDZO1ZyaHu7_1ZVtO7s6EDq2sxST7ps4vlpmRHLM_asLwGBJVNQxUOCP6RLy1gJTSXO-FoVCecsD8Fz_pqtT6CA8p0o40urKaKN85CxOvZuTqwm_5O8ga4vRWacKctXloRRNIQG-pDbpRe5X79_zwHFousQtUL3NnT4GssDIl2nZ22Y79fCyFbX_BD1oN2IBOndIQ"
  },
  @{
    Key = "host-add-vehicle"
    Screen = "Them xe moi - RentFlow Host"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzUyOWM1NzE3YmRhMzRmOGVhZDg0ZTdhNjZhNzQ4N2U3EgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uhO7w0mNyIJ-QIS7QJzFXBEUhrJrBy3NsSHMH4CjVBxkFFLJOt86yMR3etf53VTtFtFTgPHSFmUaziqc2_yEjS-lzvsZIswinR7mpn_bIwMIPWJ1AME1Nyz33SuPcfgLRLqnQJR337gSzyDgaY_BU1riVcUNYphv_5uYCXnbqUcG9MpG3ckG_9OeyZEFC3ZXK5rQIzNNmdgHAOnLk5JLVn6hiobytVsiYn6ayiUo9Y4hjaejc5XeLRURbI"
  },
  @{
    Key = "host-availability"
    Screen = "Lich kha dung - RentFlow Host"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzA4NzZjYjE3NzU4YzQwZWZiNDRjZTI1MmQ4NTI5YWFhEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ui3hFnK3JKMOu1Q4vdclkRQ9V8w1wdV5Uua9KE3yuAmcy4tBmvH54i3rs_xU71gWGib1VBzY7kD_JqQb_4k4dLYx-KXhEbqfM32os-SA1Qid4qd0S48cON9w8w60ElMQ1dD5y5UfweS9VmvdboSP0HY5_IRl7kO2qSzYvho6zG1MofEUdADFg1XU4BG7Y0eDDdov_3XQme5soZN9krZoRviMR6SJl1o7NcGAmsyzxoLbpOJbwShkXVrMusJ"
  },
  @{
    Key = "host-listing-detail"
    Screen = "Chi tiet bai dang - RentFlow Host"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzIzNThlYjgxZGUwZjRkZjBhZTMwYTZkMWIxZmJkZTRiEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ujDjr8wuxquodjYqKlLniljKzbSCvgrWtbN6V_7bcpDxvsQsucazRHjurUipyhnAuWTjHXyZDsEi6MwYwgh-ko0Y7q8uDnJToPCBjJYgdTS6aGqRIdilN1tVL4YVNBgbk8HQc5NxMLDlHbHz3PFpHtHtgqzU7EjNr-byHfP2-AVGxnqnTFOV9jLXw_cRDkhYlcnpKsTGSvlkEgL3NCQJEbxyn60Xq3OaN90TdBfqwULtH1E8eSg"
  },
  @{
    Key = "admin-system-overview"
    Screen = "Tong quan he thong - RentFlow Admin"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2ViNjk0MTdhNmYyMDQxNWQ4MWNkMDEwY2UwN2YwZDU5EgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uhhDdVg8PTrByuFb4qlOl_eRWOgkXeQ4Y-nH36-ceYv4YqRijHSSEKg-rTM89fkI-Lfbr3x915Z9kjauh-S_nH1iuc2qxGePRGS5gxG_IT52Cp7az5sF0RF3w0kPUdCQ7_c7sCqJIozb6z_cR4w3CuYmQ0y7khki9cJySm3WjpsvC2jMMzpPGv2Jl-LAmh0HoMx24Ikd93jIehrsf7PBVLUj9-jeOjuL46K1m6H3c22huKTOI7uvM3iNI4"
  },
  @{
    Key = "admin-listing-approval"
    Screen = "Phe duyet bai dang - RentFlow Admin"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzQ0ZDk5ZWJjMWNmMDRmMmNhNGZiZTQ4NDg0NzhkNjA2EgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uh3eAmkCS-W_SDDOdX_G_Ub72euTAL_H1QpcxsjMHc8L3g93LDPgMpnjZ0LGaKh9y51saFJlh87_Ub-VarEeEsx0VSmci6X5BBCaOYEZsp2FxVj_0VDkXyrsPd6G-Qb321JBs-QUXfveB9b5Go3FoDbc8i5OyoPjo85mgGXybldA3Rkj-GOQDFn5bGunaWx35kPzDGb1PO4VjPLdezeHxQnWp5YLu63M0tFjsy0acut0aGSI2ThJynVq7U"
  },
  @{
    Key = "admin-approval-detail"
    Screen = "Chi tiet phe duyet - RentFlow Admin"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzAzN2IwMThjNTkzNzQ1NWU5OWM1ZjYzZTBhNWY1ZGQwEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uj6BhX4Uexn7esAkjCHDajlPfGCfuIa_-o3KtD-MBJ0hDK6NZGpBTi2bB6XaQNNOTjCSoLiFcqbjjOCkUNLII9gWao7d1o7qLlz1eE-OkybKyqHuj87rk-tLDoADlU31P3wE38vmrTbv7MG3iuW-W5didgoMtC6sdQALtOnhrgPMqNp8AzWO7eFsQwpkYiXxs06-Fiwv4-xtwIFC1jF9mfZJOTNctbXIRCV0TPalV7t0n4v26eYDDpzOPk"
  },
  @{
    Key = "admin-driver-profile-review"
    Screen = "Duyet ho so lai xe - RentFlow Admin"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzg3MDVlYjQ3MzVhNzRhZDRhNWUzN2IwY2EyZmYzOWRjEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ugCZnyOU9nGNocjx3kWIXTw0msam0OD8lVt1F717rKUWMY5jIa7ip24VmIMpT2936XqurcwYaIwN5BEKP7UcPTulv3Z9K5UXibfkBF0fQXHXWj-NBFjNoqVLUMGqYjfySboZntJxlz4rEsM9bXM160PkKhpA2dL3xjK4d9MOB2emTVKCSU9VZpuGYnQf5J2KQOy0DMvcGnovvesCD7IsI1eb1pGM0xBvv7yggw84wmJZ5PrUfWL5YqGEanH"
  },
  @{
    Key = "admin-reject-approval"
    Screen = "Tu choi phe duyet - RentFlow Admin"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzFiNzM2NjJjZDE2MjQ1MGM4ZTAxYjQ2MTBlZGU4ZTgyEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ujJOubRhLRnFbyL-XX5nXX6VpjpIziw6QahHcuHsvKXIoiIlox-PI4-hTjwe-OBmRlNE7pMa-D--oHYzo7UmM65coK_k6HvTXQZnAH5FHRKrdh666vWcU7dNP3mwZiYmVdyPFhBlRd10z1daODD-z6IZquFP5HhDegliORMckysPhMxTqFoc4JdADENULSfX5gLOMEWCQlAEiysO_J7CwYfKTmF-IXJhYNfhFscYFGbVcBv-n5KGJGwMjw"
  },
  @{
    Key = "admin-suspend-listing"
    Screen = "Suspend Listing - RentFlow Admin"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzQyZmJhYzgzODEzMzQ3YzQ5MWFlZWJkNDIxM2YyMzRjEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ujLeriqpRayRFW3rHl_EDf58EFaMZ_UFpjBUT4U14hKJMk_ZB_Kl6leSm_WFszzb0qNusQ2jXou_5rLq23T58Vrr3GjYvQJXTEeM4hMuPR1G8AAnRqtHex_kZ_b0cjlNXTYg-HwEgJSv1IGH4XZACj70IEuV1O3pRSgCuPnxgqQn3NXuE0qbLdCMO_XFefwEyjoPRuSJrcWWwNlGFpfdpSefWYEhD72WIYB8plGgWwCJoA3q3HJ5hVlb9oS"
  },
  @{
    Key = "global-page-not-found"
    Screen = "Page Not Found - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzdiM2M3YzY5ZGNiZTQxMjdhMTIyZTA4MjNjNDUyMGIxEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ujjM97JSFC7Fw5YRg9nMav9IconkTRbQg-7DssP2CCHzH5CfCR52hm2OJi7hRaTYxDM05K8o4lRBhBam2US2312p6SMVInUbzCa1u1BPV18x4Vy8PCX9LVR-Zi7wPYi-c205EtuuoSJ1gmElR54l8Qxhm4U5CcDUGxkyuOBLCeifYNajPF6BsjQ1e9X5B0946ZdNjZEEzEu68WFh7grbKQ_-1Pz7_tHdMGLB4EL8uIWNGufxEJjdOu_8lRE"
  },
  @{
    Key = "global-system-error"
    Screen = "System Error - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2Q5YWM0MWJmNTVkNDRkZTc5YjIzM2M4MWNlNTcxMzNlEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uhHFrwea6Wo3jn7-ngA0GFNzj2R-KaEb2MJRUhPba74ZFHWhgkJwbaUpQa5jdS5An_UaMd4mdtFletDCFU_Q_C20d2xcIFHUs6wyM8LqWeYq7tUlTjMD755MMAqBQDCU53S7oA9FSVHl5cdm6u_wes7MZX9KUP2FVg9LWes0C5EhnL8MNieU7VqmiG0T0HvZeRxCO9ReCR7nyuPhdj3am6MBp84CTNrMTkg1eQMMtu5w7pg46PfekgQfjqX"
  },
  @{
    Key = "global-states-skeletons"
    Screen = "States & Skeletons - RentFlow Design System"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2FjMWEzOTJiZjIyZDQxOTFhNmJlMDgzYTYzNDg3MTNmEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ujzR3qe-K5eAcms_oY3Ry3RkIlKqvLEQWfGLnttX5rpgx1sc5ob73E7BKXfY4LJ4KQWw0QBjr8bimMyGZg2t4ONluqvgMXX0egQPdEu8w73j0scykBheYq_sAsfx9hxNYNLe8uKJsywAt_eiNncx87JLxoDpvCQYEQH3UAS7y9irRLTyZxN4vdoBG8VxRlvSaFbY3yfvG-H3Bq6lZqeWJrD-03rOrjBNsnGyKsIWcnXi0XCYfHeRMT1lkd_"
  },
  @{
    Key = "global-component-kit"
    Screen = "RentFlow Component Kit"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzQ4Zjc2Y2FmZTY2ODQ4Zjg5MWZkYmU5YWY0NWIxYWIwEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ugU9nJxUWZHdQj6y92Nexl8miHU5LpvOrX3N-xhKZI3qq5c2zvcJNFXMsvCWmdmIZVFY6pQTej5mGGwH0VONKgps2AxuwLLT5LfWKuz5J7bOlfiXAlWTHS3lbPDMaNDnfat0sKtPX_vSKSvRRDYuGA3uGqyu_eDJNFDBmm6lC-n9QcTUU083tX9lZKku-Ea4yKD113ZKuV6tG00GcP7Rdhp4NAgWO-baC_ycA28gLSzSMUpny41kVr-QCsc"
  },
  @{
    Key = "future-payment"
    Screen = "Thanh toan - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzAwYzAxZmUyN2I5ZDQ3Nzc4NjY5ZDY5NGZkMzEwMjM5EgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uiUlFZ6r18Gqqg9SHdFJ2mcF5fAT8V2rc_mq-ej6Uz41Hb8DYtM-M99VERufYdVPIOjsXZhDPyXvHx0swyF3QFW04gNN14y0PM0Dafy44GMy81R6F9SMlmWr53O-dkXdQiHUJJR20Aqps76M647qfF1n5yXt33_1QVPbsvq8vEy9qZ-wBvO46YE75g68b9jgHPY_UbCAUhvy9inpTjaMKM7Ypc428ty8jchBedk-_5tMIH7cvxZSyBTf_qK"
  },
  @{
    Key = "future-driver-verification"
    Screen = "Xac thuc bang lai - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2M3NGQ0ZGM4MjVlNTQ4NGY4NTUyOWEzMTNlZTA3MWQ2EgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ugi7KaEHHRnxXwSmAErGeyXvMTkdX44pQODoZU32gA7kKzK18F10VFa22KfJWzoodPw5YGGmOapuAQwoygA1iAnh43QRNVKgHBXGxRjgEj0UV_q86wMLh_IJOPpze35K25sZ4byLrxEMsIky-2dNYMn7QJFMX_gi-KWVMu7eNZMhNokGaiSAdCfHS8CqJUNI_cn8IpgI2AtbCYzXUYKj6Qbg0_VUdu0cr_NmzLyTw-eq6mt8347q_THWwLY"
  },
  @{
    Key = "future-audit-logs"
    Screen = "Nhat ky he thong - RentFlow Admin"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sXzU2NzU2NTAyZjI0YjQyNWQ4NDAwYmE1NzdhN2U0NDVmEgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0uhppHOtpR3ZNRi3L8JlQJqC9ZNvyYFbkbVkAMcchuLR7iqRG4TK_TH0ZLYDrbplVIDj9vTbDBxgka-Vw8_fTEwTTCzSH8pZ7VdkW-KzxBDbyv9gATC2DaXc4p4X_6fhAqzzsdPVIea9A7JE8pqMyaH9tD_Xi_y70S-l7_YdWPH_UfJ0_kxwiaH1ZSUK1XU2dvZKz_oJzR-mlDkkUJzFjfJfItpC-f7VG0jm0SAMr2VbJQ2jHpbhrYcWgSo"
  },
  @{
    Key = "future-support-center"
    Screen = "Trung tam ho tro - RentFlow"
    Html = "https://contribution.usercontent.google.com/download?c=CgthaWRhX2NvZGVmeBJ8Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpbCiVodG1sX2JmNGM1OGQxNTczMDRhMWI4ZmE1NmZlZjVkMWFjOWU5EgsSBxCos9ivygMYAZIBJAoKcHJvamVjdF9pZBIWQhQxNDQzNTEyMTg5MjkxNjMwNjMxOA&filename=&opi=89354086"
    Screenshot = "https://lh3.googleusercontent.com/aida/ADBb0ui9Gb6xp7LlTeum29TYbjMHVH3I025EScGz2fiJ5ctivXXZFjdNAX3xyy93Wef9X8dNGFRrRmlZaAEJpxRcIUT6eg_vQ7ZBKkbakAVRcUFNcvuysgvtLED7emylsBzlYq2J_Imsc0lNvG67wrBy0qTRa0-4SHpHiLXbBvXkBYye-ExmNjJZCq03GKdoYc7VbkIqYheS6cQ32lxvy4dP_CUnPvbsVDxoarRAxLqV2KOH9hpKnlO77tWLvUlT"
  }
)

$manifest = foreach ($screen in $screens) {
  $htmlPath = Join-Path $htmlDir "$($screen.Key).html"
  $screenshotPath = Join-Path $shotDir "$($screen.Key).png"

  Invoke-WebRequest -Uri $screen.Html -OutFile $htmlPath
  Invoke-WebRequest -Uri $screen.Screenshot -OutFile $screenshotPath

  [PSCustomObject]@{
    key = $screen.Key
    screen = $screen.Screen
    html = "html/$($screen.Key).html"
    screenshot = "screenshots/$($screen.Key).png"
  }
}

$manifest | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 -Path (Join-Path $root "manifest.json")

Write-Host "Downloaded $($screens.Count) Stitch screens to $root"
