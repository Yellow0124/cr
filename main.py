from crawlers.kktix_crawler import get_global_events, scrape_kktix_event_detail


def main():
    # 使用標籤篩選網址 (音樂類 1, 7)
    base_url = "https://kktix.com/events?utf8=%E2%9C%93&search=&max_price=&min_price=&start_at=&end_at=&event_tag_ids_in=1%2C7"

    print("🚀 [系統] KKTIX 全自動掃蕩引擎啟動...")
    event_urls = get_global_events(base_url)

    if not event_urls:
        print("❌ [錯誤] 找不到活動網址。")
        return

    print(f"\n🎯 [系統] 偵測到 {len(event_urls)} 個音樂活動，開始深度解析...\n")

    success_count = 0
    for i, url in enumerate(event_urls, 1):
        try:
            event_data = scrape_kktix_event_detail(url)
            if event_data:
                success_count += 1
                print("=" * 60)
                print(f"📌 【活動名稱】: {event_data['event_name']}")
                print(f"   演出藝人: {event_data['artist']}")
                print(f"   活動日期: {event_data['event_date']}")
                print(
                    f"   啟售時間: {event_data['sale_date']} ({event_data['sale_time_only']})")
                print(f"   活動地點: {event_data['location']}")
                print(f"   活動地址: {event_data.get('address', '未取得')}")
                print(f"   活動連結: {event_data['original_url']}")

                # 🌟 票種與票價逐行顯示
                if event_data.get('tickets'):
                    # 提取所有票種名稱並串接
                    ticket_types = [t['ticket_type']
                                    for t in event_data['tickets']]
                    # 提取所有價格並串接 (這裡不用加上千分位，維持你要求的純數字格式)
                    ticket_prices = [str(t['price'])
                                     for t in event_data['tickets']]

                    print(f"   票種: {', '.join(ticket_types)}")
                    print(f"   票價: {', '.join(ticket_prices)} 元")
                else:
                    print("   票種: 未取得")
                    print("   票價: 未取得")

                print("=" * 60)
        except Exception as e:
            print(f"💥 錯誤: {e}")

    print(f"\n [完成] 共成功抓取 {success_count} 場活動！資訊已全數爬清楚。")


if __name__ == "__main__":
    main()
