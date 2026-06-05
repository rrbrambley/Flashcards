import SwiftUI

/// The signed-in shell: a three-tab bar matching Android's bottom navigation (Home / New /
/// Library). Each tab is its own `NavigationStack` so feature screens can push detail/create/edit/
/// practice views later. Tab content is placeholder until the feature tickets fill it in.
struct MainTabView: View {
    private enum Tab {
        case home, new, library
    }

    @State private var selection: Tab = .home

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack { HomeView() }
                .tabItem { Label("Home", systemImage: "house") }
                .tag(Tab.home)

            NavigationStack { CreateDeckView() }
                .tabItem { Label("New", systemImage: "plus.square") }
                .tag(Tab.new)

            NavigationStack { LibraryView() }
                .tabItem { Label("Library", systemImage: "rectangle.stack") }
                .tag(Tab.library)
        }
    }
}
