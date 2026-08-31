import UIKit

// Classic explicit main.swift instead of @UIApplicationMain/@main on AppDelegate - mirrors
// KorGE's own generated main.m (int main() { UIApplicationMain(...) }) and avoids depending on
// whichever @main/@UIApplicationMain attribute behavior the CI runner's exact Xcode version has,
// since this can't be checked against a real Xcode locally.
UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv, nil, NSStringFromClass(AppDelegate.self))
