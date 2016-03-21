package nu.nota.pipeline.braille.impl;

import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.copyOfRange;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import static com.google.common.collect.Iterables.size;
import static com.google.common.collect.Lists.newArrayList;

import org.daisy.pipeline.braille.common.AbstractBrailleTranslator;
import org.daisy.pipeline.braille.common.AbstractTransformProvider;
import org.daisy.pipeline.braille.common.AbstractTransformProvider.util.Function;
import org.daisy.pipeline.braille.common.AbstractTransformProvider.util.Iterables;
import static org.daisy.pipeline.braille.common.AbstractTransformProvider.util.Iterables.concat;
import static org.daisy.pipeline.braille.common.AbstractTransformProvider.util.Iterables.transform;
import static org.daisy.pipeline.braille.common.AbstractTransformProvider.util.logCreate;
import static org.daisy.pipeline.braille.common.AbstractTransformProvider.util.logSelect;
import org.daisy.pipeline.braille.common.BrailleTranslator;
import org.daisy.pipeline.braille.common.BrailleTranslatorProvider;
import org.daisy.pipeline.braille.common.Query;
import org.daisy.pipeline.braille.common.Query.Feature;
import org.daisy.pipeline.braille.common.Query.MutableQuery;
import static org.daisy.pipeline.braille.common.Query.util.mutableQuery;
import org.daisy.pipeline.braille.common.TransformProvider;
import static org.daisy.pipeline.braille.common.TransformProvider.util.dispatch;
import static org.daisy.pipeline.braille.common.TransformProvider.util.memoize;
import static org.daisy.pipeline.braille.common.util.Locales.parseLocale;
import static org.daisy.pipeline.braille.common.util.Strings.join;
import static org.daisy.pipeline.braille.common.util.URIs.asURI;
import org.daisy.pipeline.braille.libhyphen.LibhyphenHyphenator;
import org.daisy.pipeline.braille.liblouis.LiblouisTranslator;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.ComponentContext;

public interface NotaTranslator {
	
	@Component(
		name = "nu.nota.pipeline.braille.impl.NotaTranslatorProvider",
		service = {
			TransformProvider.class,
			BrailleTranslatorProvider.class
		}
	)
	public class Provider extends AbstractTransformProvider<BrailleTranslator> implements BrailleTranslatorProvider<BrailleTranslator> {
		
		private URI href;
		
		@Activate
		private void activate(ComponentContext context, final Map<?,?> properties) {
			href = asURI(context.getBundleContext().getBundle().getEntry("xml/block-translate.xpl"));
		}
		
		private final static Query uncontractedTable = mutableQuery().add("liblouis-table", "http://www.liblouis.org/tables/da-dk-g16.utb");
		private final static Query contractedTable = mutableQuery().add("liblouis-table", "http://www.liblouis.org/tables/da-dk-g26.ctb");
		private final static Query hyphenTable = mutableQuery().add("libhyphen-table",
		                                                            "http://www.libreoffice.org/dictionaries/hyphen/hyph_da_DK.dic");
		
		private final static Iterable<BrailleTranslator> empty = Iterables.<BrailleTranslator>empty();
		
		private final static List<String> supportedInput = ImmutableList.of("css","text-css","dtbook","html");
		private final static List<String> supportedOutput = ImmutableList.of("css","braille");
		
		/**
		 * Recognized features:
		 *
		 * - translator: Will only match if the value is `nota'.
		 * - locale: Will only match if the language subtag is 'da'.
		 * - grade: `0' or `2'.
		 *
		 */
		protected final Iterable<BrailleTranslator> _get(Query query) {
			final MutableQuery q = mutableQuery(query);
			for (Feature f : q.removeAll("input"))
				if (!supportedInput.contains(f.getValue().get()))
					return empty;
			for (Feature f : q.removeAll("output"))
				if (!supportedOutput.contains(f.getValue().get()))
					return empty;
			if (q.containsKey("locale"))
				if (!"da".equals(parseLocale(q.removeOnly("locale").getValue().get()).getLanguage()))
					return empty;
			if (q.containsKey("translator"))
				if ("nota".equals(q.removeOnly("translator").getValue().get()))
					if (q.containsKey("grade")) {
						String v = q.removeOnly("grade").getValue().get();
						final int grade;
						if (v.equals("0"))
							grade = 0;
						else if (v.equals("2"))
							grade = 2;
						else
							return empty;
						if (q.isEmpty()) {
							Iterable<LibhyphenHyphenator> hyphenators = logSelect(hyphenTable, libhyphenHyphenatorProvider);
							final Query liblouisTable = grade == 0 ? uncontractedTable : contractedTable;
							return concat(
								transform(
									hyphenators,
									new Function<LibhyphenHyphenator,Iterable<BrailleTranslator>>() {
										public Iterable<BrailleTranslator> _apply(final LibhyphenHyphenator h) {
											return concat(
												transform(
													logSelect(mutableQuery(liblouisTable).add("hyphenator", h.getIdentifier()),
													          liblouisTranslatorProvider),
													new Function<LiblouisTranslator,Iterable<BrailleTranslator>>() {
														public Iterable<BrailleTranslator> _apply(final LiblouisTranslator translator) {
															return transform(
																logSelect(mutableQuery(uncontractedTable).add("hyphenator", h.getIdentifier()),
																          liblouisTranslatorProvider),
																new Function<LiblouisTranslator,BrailleTranslator>() {
																	public BrailleTranslator _apply(LiblouisTranslator uncontractedTranslator) {
																		return __apply(
																			logCreate(
																				new TransformImpl(grade, translator, uncontractedTranslator)));
																	}}); }})); }})); }}
			return empty;
		}
		
		private final static Splitter.MapSplitter CSS_PARSER
		= Splitter.on(';').omitEmptyStrings().withKeyValueSeparator(Splitter.on(':').limit(2).trimResults());
		private final static Splitter TEXT_TRANSFORM_PARSER = Splitter.on(' ').omitEmptyStrings().trimResults();
		
		private class TransformImpl extends AbstractBrailleTranslator {
			
			private final FromStyledTextToBraille translator;
			private final FromStyledTextToBraille uncontractedTranslator;
			private final XProc xproc;
			private final int grade;
			
			private TransformImpl(int grade, LiblouisTranslator translator, LiblouisTranslator uncontractedTranslator) {
				Map<String,String> options = ImmutableMap.of(
					"text-transform", mutableQuery().add("id", this.getIdentifier()).toString(),
					"contraction-grade", ""+grade);
				xproc = new XProc(href, null, options);
				this.grade = grade;
				this.translator = translator.fromStyledTextToBraille();
				this.uncontractedTranslator = uncontractedTranslator.fromStyledTextToBraille();
			}
			
			@Override
			public XProc asXProc() {
				return xproc;
			}
			
			@Override
			public FromStyledTextToBraille fromStyledTextToBraille() {
				return fromStyledTextToBraille;
			}
			
			private final FromStyledTextToBraille fromStyledTextToBraille = new FromStyledTextToBraille() {
				public java.lang.Iterable<String> transform(java.lang.Iterable<CSSStyledText> styledText) {
					int size = size(styledText);
					String[] text = new String[size];
					String[] style = new String[size];
					int i = 0;
					for (CSSStyledText t : styledText) {
						text[i] = t.getText();
						style[i] = t.getStyle();
						i++; }
					return Arrays.asList(TransformImpl.this.transform(text, style));
				}
			};
			
			private String[] transform(String[] text, String[] cssStyle) {
				if (text.length == 0)
					return new String[]{};
				String[] result = new String[text.length];
				boolean uncont = false;
				int j = 0;
				List<String> uncontStyle = null;
				for (int i = 0; i < text.length; i++) {
					Map<String,String> style = new HashMap<String,String>(CSS_PARSER.split(cssStyle[i]));
					List<String> textTransform = null; {
						String t = style.remove("text-transform");
						if (t != null)
							textTransform = newArrayList(TEXT_TRANSFORM_PARSER.split(t)); }
					if (textTransform != null && textTransform.remove("uncontracted")) {
						if (i > 0 && !uncont)
							for (String s : transformArray(translator,
							                               copyOfRange(text, j, i),
							                               copyOfRange(cssStyle, j, i)))
								result[j++] = s;
						if (uncontStyle == null)
							uncontStyle = new ArrayList<String>();
						String newStyle = "";
						for (Map.Entry<String,String> kv : style.entrySet())
							newStyle += (kv.getKey() + ": " + kv.getValue() + ";");
						if (textTransform != null && textTransform.size() > 0)
							newStyle += ("text-transform: " + join(textTransform, " ") + ";");
						uncontStyle.add(newStyle);
						uncont = true; }
					else {
						if (i > 0 && uncont) {
							for (String s : transformArray(uncontractedTranslator,
							                               copyOfRange(text, j, i),
							                               uncontStyle.toArray(new String[i - j])))
								result[j++] = s;
							uncontStyle = null; }
						uncont = false; }}
				if (uncont)
					for (String s : transformArray(uncontractedTranslator,
					                               copyOfRange(text, j, text.length),
					                               uncontStyle.toArray(new String[text.length - j])))
						result[j++] = s;
				else
					for (String s : transformArray(translator,
					                               copyOfRange(text, j, text.length),
					                               copyOfRange(cssStyle, j, text.length)))
						result[j++] = s;
				return result;
			}
			
			private String[] transformArray(FromStyledTextToBraille translator, String[] text, String[] style) {
				List<CSSStyledText> styledText = new ArrayList<CSSStyledText>();
				for (int i = 0; i < text.length; i++)
					styledText.add(new CSSStyledText(text[i], style[i]));
				String[] result = new String[text.length];
				int i = 0;
				for (String s : translator.transform(styledText))
					result[i++] = s;
				return result;
			}
			
			@Override
			public String toString() {
				return Objects.toStringHelper(NotaTranslator.class.getSimpleName())
					.add("grade", grade)
					.add("id", getIdentifier())
					.toString();
			}
		}
		
		@Reference(
			name = "LiblouisTranslatorProvider",
			unbind = "unbindLiblouisTranslatorProvider",
			service = LiblouisTranslator.Provider.class,
			cardinality = ReferenceCardinality.MULTIPLE,
			policy = ReferencePolicy.DYNAMIC
		)
		protected void bindLiblouisTranslatorProvider(LiblouisTranslator.Provider provider) {
			liblouisTranslatorProviders.add(provider);
		}
	
		protected void unbindLiblouisTranslatorProvider(LiblouisTranslator.Provider provider) {
			liblouisTranslatorProviders.remove(provider);
			liblouisTranslatorProvider.invalidateCache();
		}
	
		private List<TransformProvider<LiblouisTranslator>> liblouisTranslatorProviders
		= new ArrayList<TransformProvider<LiblouisTranslator>>();
		private TransformProvider.util.MemoizingProvider<LiblouisTranslator> liblouisTranslatorProvider
		= memoize(dispatch(liblouisTranslatorProviders));
		
		@Reference(
			name = "LibhyphenHyphenatorProvider",
			unbind = "unbindLibhyphenHyphenatorProvider",
			service = LibhyphenHyphenator.Provider.class,
			cardinality = ReferenceCardinality.MULTIPLE,
			policy = ReferencePolicy.DYNAMIC
		)
		protected void bindLibhyphenHyphenatorProvider(LibhyphenHyphenator.Provider provider) {
			libhyphenHyphenatorProviders.add(provider);
		}
	
		protected void unbindLibhyphenHyphenatorProvider(LibhyphenHyphenator.Provider provider) {
			libhyphenHyphenatorProviders.remove(provider);
			libhyphenHyphenatorProvider.invalidateCache();
		}
	
		private List<TransformProvider<LibhyphenHyphenator>> libhyphenHyphenatorProviders
		= new ArrayList<TransformProvider<LibhyphenHyphenator>>();
		private TransformProvider.util.MemoizingProvider<LibhyphenHyphenator> libhyphenHyphenatorProvider
		= memoize(dispatch(libhyphenHyphenatorProviders));
		
	}
}
