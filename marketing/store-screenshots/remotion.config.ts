import {Config} from '@remotion/cli/config';

Config.setEntryPoint('src/index.ts');
Config.setBrowserExecutable(
	'../playstore-video/node_modules/.remotion/chrome-headless-shell/win64/chrome-headless-shell-win64/chrome-headless-shell.exe',
);
